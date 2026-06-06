# Use Cases — Order Service

Сценарии использования текущей реализации order-service.
Сквозная корреляция во всех сценариях: **businessKey процесса = orderId**.

---

## UC-1. Создание и успешная обработка заказа (happy path)

| | |
|---|---|
| **Актор** | Customer / API Client (Postman, фронтенд, другой сервис) |
| **Цель** | Создать заказ и довести его до доставки |
| **Предусловия** | Доступны Process Engine (:8080), Keycloak (:8180), RabbitMQ (:5672); order-service запущен (:8081); BPMN-схемы задеплоены (auto-deploy) |
| **Триггер** | `POST /orders` с телом `CreateOrderRequest` |

### Основной поток

1. Клиент отправляет `POST /orders` (firstName, lastName, itemId, amount,
   quantity, deliveryAddress).
2. `OrderController` → `OrderService.createOrder()`: заказ сохраняется в БД
   со статусом **CREATED** (id — UUID).
3. `ProcessInstanceClient` получает JWT у Keycloak (или из кэша) и вызывает
   `POST /api/v1/instances` (definitionKey=`order-process`,
   businessKey=orderId, variables={orderId}).
4. `processInstanceId` сохраняется в заказе; клиент получает
   **201 Created** `{id, status=CREATED, processInstanceId}`.
5. Движок исполняет процесс, публикуя external tasks в RabbitMQ:
   1. `order.validate` → `OrderValidateTaskHandler` → статус **VALIDATED**;
   2. параллельно: `order.book` (→ статус **BOOKED**, ~5s) и `order.notify`
      (уведомление о бронировании);
   3. сабпроцесс оплаты: `order.payment.charge` →
      `OrderPaymentChargeTaskHandler` → `isPaymentSuccess=true`,
      статус **PAID**;
   4. `order.deliver` → `OrderDeliverTaskHandler` → статус **DELIVERED**.
6. Процесс достигает End Event и завершается (state COMPLETED).

### Постусловия

- Заказ в БД в статусе **DELIVERED**; каждая задача процесса выполнена
  ровно один раз; компенсация (`refund-payment`) **не** запускалась.

### Альтернативные потоки

- **A1. Отсутствует тело запроса** → HTTP 400, заказ не создаётся.
- **A2. Недоступен Process Engine / Keycloak** → исключение, HTTP 5xx;
  (заметка: заказ к этому моменту уже сохранён в транзакции — поведение
  определяется границами `@Transactional` в `OrderService.createOrder`).

*Покрытие тестами:* `OrderControllerIntegrationTest`
(shouldCreateOrderSuccessfully, shouldPassCorrectVariablesToProcessEngine,
shouldReturn400WhenRequestBodyMissing,
shouldPropagateExceptionWhenProcessEngineFails),
`OrderProcessIntegrationTest.happyPath`.

---

## UC-2. Повторная попытка оплаты (retry-петля платёжного сабпроцесса)

| | |
|---|---|
| **Актор** | Process Engine (системный актор; инициируется UC-1) |
| **Цель** | Довести списание оплаты до успеха при временных сбоях платежа |
| **Предусловия** | Процесс дошёл до call activity Charge Payment (`charge-payment-subprocess`) |
| **Триггер** | External task `order.payment.charge` |

### Основной поток

1. `OrderPaymentChargeTaskHandler` пытается списать оплату.
2. Списание не проходит — бросается `PaymentChargeException`
   (code `30000`, "Payment charge failed").
3. Воркер завершает задачу **бизнес-результатом**:
   `complete({isPaymentSuccess=false})` (не technical error).
4. Exclusive gateway по `${isPaymentSuccess}` направляет процесс в
   `order.payment.status` → `OrderPaymentStatusHandler` проверяет статус
   платежа и инкрементирует `retryCount`.
5. Если `isPaymentSuccess` всё ещё `false` — таймер **PT5S** (пауза 5 секунд),
   затем возврат к шагу 1.
6. При успешном списании (`isPaymentSuccess=true`) — заказ переходит
   в статус **PAID**, сабпроцесс завершается, основной процесс продолжается
   доставкой (UC-1, шаг 5.4).

### Постусловия

- Успех: статус **PAID**, процесс продолжается.
- Исчерпание попыток: см. A1.

### Альтернативные потоки

- **A1. retryCount > 5** — воркер списания возвращает техническую ошибку
  `context.error(code, message)` → инцидент в Process Engine; процесс
  останавливается до ручного вмешательства оператора.

---

## UC-3. Компенсация: возврат платежа при сбое доставки

| | |
|---|---|
| **Актор** | Process Engine (системный актор; инициируется UC-1) |
| **Цель** | Откатить списание оплаты, если доставка не удалась (saga) |
| **Предусловия** | Оплата успешно списана (Charge Payment завершён, статус **PAID**) |
| **Триггер** | Ошибка в задаче `order.deliver` (`OrderDeliverTaskHandler` бросает RuntimeException, напр. при отсутствии `orderId`) |

### Основной поток

1. `OrderDeliverTaskHandler` завершается ошибкой — доставка не выполнена.
2. Движок активирует **boundary compensation event**, привязанный
   к call activity Charge Payment.
3. Запускается компенсационная задача Refund Payment
   (`isForCompensation=true`): external task `order.payment.refund` →
   `OrderPaymentRefundTaskHandler` выполняет возврат платежа
   и завершает задачу (`complete({processedAt})`).
4. Процесс переходит в состояние ERROR / COMPENSATING — обработка заказа
   остановлена с откатом платежа.

### Постусловия

- Платёж возвращён (задача `refund-payment` выполнена); заказ **не**
  переходит в статус DELIVERED; инцидент виден в Process Engine.

### Альтернативные потоки

- **A1. Доставка успешна** — компенсация не активируется, процесс
  завершается нормально (UC-1).

*Покрытие тестами:* `OrderProcessIntegrationTest.compensationFlow`.

---

## Сводная карта статусов заказа

```
CREATED ──(order.validate)──▶ VALIDATED ──(order.book)──▶ BOOKED
   BOOKED ──(order.payment.charge, успех)──▶ PAID
   PAID ──(order.deliver, успех)──▶ DELIVERED
   PAID ──(order.deliver, ошибка)──▶ компенсация refund (статус остаётся PAID)
```
