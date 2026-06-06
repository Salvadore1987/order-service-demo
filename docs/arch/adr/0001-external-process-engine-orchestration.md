# ADR-0001: Оркестрация жизненного цикла заказа через внешний BPMN Process Engine

## Статус

Принято

## Контекст

Жизненный цикл заказа состоит из нескольких шагов (валидация, бронирование,
уведомление, оплата, доставка), часть из которых выполняется параллельно,
часть требует повторных попыток (оплата) и компенсации (возврат платежа при
сбое доставки). Зашивать такую state machine в код сервиса (if/else, статусы,
шедулеры ретраев) — сложно поддерживать и невозможно визуализировать.

## Решение

Оркестрацию выносим во внешний **Process Engine** (Camunda Platform 7,
external task pattern). Процесс описывается декларативно в BPMN:

- `src/main/resources/bpmn/order-process.bpmn` — основной процесс
  (id: `order-process`): Validate → Parallel(Book + Notify) → Charge Payment
  (call activity) → Deliver, с boundary compensation event на оплате;
- `src/main/resources/bpmn/charge-payment-subprocess.bpmn` — сабпроцесс
  оплаты с retry-петлёй (charge → check status → timer PT5S → charge).

Order Service:

- запускает процесс через REST (`ProcessInstanceClient` →
  `POST /api/v1/instances`), передавая `definitionKey = order-process`,
  **businessKey = orderId** и variables `{orderId}`;
- хранит `processInstanceId` в сущности `Order` для корреляции;
- BPMN-схемы деплоятся автоматически при старте
  (`process-engine.worker.auto-deploy.enabled: true`,
  `resource-location: classpath:bpmn/`).

## Последствия

**Плюсы:**

- Поток процесса визуализируем и редактируем в Camunda Modeler.
- Параллелизм, таймеры, ретраи и компенсация — стандартные элементы BPMN,
  без самописной инфраструктуры.
- Состояние процесса персистентно в движке: рестарт сервиса не теряет заказы.
- businessKey = orderId даёт сквозную корреляцию заказа и процесса.

**Минусы:**

- Появляется внешняя runtime-зависимость (Process Engine, :8080) — без неё
  `POST /orders` падает.
- Состояние заказа размазано: статус в локальной БД + состояние процесса
  в движке; требуется дисциплина синхронизации (воркеры обновляют статус).
- Требуются инфраструктура аутентификации
  ([ADR-0003](0003-oauth2-client-credentials-keycloak.md))
  и транспорт задач ([ADR-0002](0002-external-task-worker-pattern-rabbitmq.md)).
