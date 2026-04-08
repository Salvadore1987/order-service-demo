# Order Service Demo

Демо-сервис обработки заказов на Spring Boot с оркестрацией через BPMN-процесс на базе [Process Manager Engine](https://github.com/Salvadore1987/process-manager-engine).

## Стек технологий

- Java 21, Spring Boot 3.4
- Spring Data JPA (H2 in-memory)
- Worker Spring Boot Starter (RabbitMQ)
- Keycloak (OAuth2 Client Credentials)

## Архитектура

Сервис реализует паттерн **External Task Worker**: бизнес-логика выполняется воркерами, которые получают задачи через RabbitMQ от Process Engine.

### BPMN-процесс заказа

```
Start --> Validate Order
              |
         +----+----+
         |         |
    Book Order  Notify Booking
         |         |
         +----+----+
              |
       Charge Payment  (подпроцесс с retry)
         |         |
         |    [ошибка] --> Refund Payment (компенсация)
         |
      Deliver Order --> End
```

### Воркеры

| Топик                   | Класс                        | Действие                          |
|-------------------------|------------------------------|-----------------------------------|
| `order.validate`        | `OrderValidateTaskHandler`   | Валидация заказа                  |
| `order.book`            | `OrderBookTaskHandler`       | Бронирование (5s задержка)        |
| `order.notify`          | `OrderNotifyTaskHandler`     | Уведомление о бронировании        |
| `order.payment.charge`  | `OrderPaymentChargeTaskHandler` | Списание оплаты               |
| `order.payment.status`  | `OrderPaymentStatusHandler`  | Проверка статуса платежа          |
| `order.payment.refund`  | `OrderPaymentRefundTaskHandler` | Возврат (компенсация)          |
| `order.deliver`         | `OrderDeliverTaskHandler`    | Доставка заказа                   |

## Требования

- Java 21+
- Process Engine на `localhost:8080`
- Keycloak на `localhost:8180`
- RabbitMQ на `localhost:5672`
- Redis на `localhost:6379`

## Сборка и запуск

```bash
# Сборка
./gradlew build

# Запуск
./gradlew bootRun
```

Сервис запускается на порту `8081`. При старте BPMN-определения автоматически деплоятся в Process Engine.

## API

### Создание заказа

```http
POST http://localhost:8081/orders
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "itemId": 1,
  "amount": "100.00",
  "quantity": 2,
  "deliveryAddress": {
    "city": "Tashkent",
    "country": "UZB",
    "street": "A. Navoi",
    "district": "Mirzo-Ulugbek",
    "house": "8",
    "flat": 54
  }
}
```

Ответ (`201 Created`):
```json
{
  "id": "uuid",
  "status": "CREATED",
  "processInstanceId": "uuid"
}
```

## Тесты

```bash
# Все unit-тесты
./gradlew test --tests "uz.salvadore.orderservice.controller.*"

# Integration-тесты (требуют запущенную инфраструктуру)
./gradlew test --tests "uz.salvadore.orderservice.process.*"
```

## Конфигурация

Основные параметры задаются через переменные окружения или `application.yml`:

| Переменная               | По умолчанию       | Описание                      |
|--------------------------|---------------------|-------------------------------|
| `REDIS_HOST`             | `localhost`         | Хост Redis                    |
| `REDIS_PORT`             | `6379`              | Порт Redis                    |
| `KEYCLOAK_CLIENT_ID`     | `order-service`     | Client ID для Keycloak        |
| `KEYCLOAK_CLIENT_SECRET` | (задан в yml)       | Client Secret для Keycloak    |
