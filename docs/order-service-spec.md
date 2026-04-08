# Order Service — Спецификация

## Overview

Демо-сервис обработки заказов, предназначенный для тестирования движка [process-manager-engine](https://github.com/Salvadore1987/process-manager-engine). Сервис предоставляет REST API для создания заказов, сохраняет их в H2, запускает BPMN-процесс в Process Engine и обрабатывает задачи процесса через RabbitMQ.

## Requirements

### Functional Requirements

1. REST endpoint `POST /orders` — принимает заказ, сохраняет в БД со статусом `CREATED`, запускает процесс `order-process` в Process Engine
2. Автоматический деплой BPMN-процесса `order-process.bpmn` в Process Engine при старте приложения (через `ApplicationRunner`)
3. RabbitMQ-воркеры (listeners) для каждого таска процесса — только логирование, без бизнес-логики
4. Обновление статуса заказа в БД при обработке каждого таска

### Non-Functional Requirements

- Демо-сервис, валидация входных данных не требуется
- H2 in-memory база данных
- Порт приложения: `8081` (Process Engine занимает `8080`)

## Architecture

### Technology Stack

- Java 17+, Spring Boot 3.x
- Spring Data JPA + H2
- Spring AMQP (RabbitMQ)
- RestClient / WebClient для REST-вызовов к Process Engine
- Gradle (Kotlin DSL)

### Component Overview

```
REST Controller
    └─> OrderService
            ├─> OrderRepository (H2)
            └─> ProcessEngineClient (REST → Process Engine)

RabbitMQ Listeners (per topic)
    └─> OrderService (обновление статуса) + логирование
        └─> RabbitTemplate (отправка результата в Process Engine)
```

### Взаимодействие с Process Engine

- **REST API** (`http://localhost:8080`):
  - `POST /api/v1/definitions` — деплой BPMN (multipart file)
  - `POST /api/v1/instances` — создание экземпляра процесса
- **RabbitMQ** (exchange: `process-engine.tasks`, topic):
  - Получение задач: очередь `task.{topic}.execute`
  - Отправка результата: routing key `task.{topic}.result`

## Data Model

### Order Entity

| Поле             | Тип            | Описание                    |
|------------------|----------------|-----------------------------|
| id               | UUID (v7)      | Primary key                 |
| firstName        | String         | Имя заказчика               |
| lastName         | String         | Фамилия заказчика           |
| itemId           | Long           | ID товара                   |
| amount           | BigDecimal     | Сумма заказа                |
| quantity         | Integer        | Количество                  |
| status           | OrderStatus    | Текущий статус заказа       |
| processInstanceId| String         | ID экземпляра процесса      |
| createdAt        | LocalDateTime  | Дата создания               |

### DeliveryAddress (Embedded)

| Поле     | Тип    |
|----------|--------|
| city     | String |
| country  | String |
| street   | String |
| district | String |
| house    | String |
| flat     | Integer|

### OrderStatus (Enum)

```
CREATED → VALIDATED → BOOKED → PAID → DELIVERED
```

## API Design

### Создание заказа

| Method | Path     | Description      |
|--------|----------|------------------|
| POST   | /orders  | Создать заказ    |

**Request Body:**
```json
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

**Response (201 Created):**
```json
{
  "id": "019577a0-...",
  "status": "CREATED",
  "processInstanceId": "inst-uuid"
}
```

### Логика обработки запроса

1. Маппинг DTO → Entity
2. Сохранение в H2 со статусом `CREATED`
3. Вызов `POST http://localhost:8080/api/v1/instances` с телом:
   ```json
   {
     "definitionKey": "order-process",
     "variables": {
       "orderId": "019577a0-...",
       "orderAmount": 100.00,
       "customerId": "John Doe",
       "itemId": 1,
       "quantity": 2
     }
   }
   ```
4. Сохранение `processInstanceId` в Order entity
5. Возврат ответа клиенту

## RabbitMQ Workers

Для каждого топика BPMN-процесса создаётся listener. Все воркеры следуют единому паттерну:

### Топики и маппинг на статусы

| Topic                   | Очередь (execute)                    | Статус после обработки |
|-------------------------|--------------------------------------|------------------------|
| `order.validate`        | `task.order.validate.execute`        | `VALIDATED`            |
| `order.book`            | `task.order.book.execute`            | `BOOKED`               |
| `order.notify`          | `task.order.notify.execute`          | — (только лог)         |
| `order.payment.charge`  | `task.order.payment.charge.execute`  | `PAID`                 |
| `order.payment.refund`  | `task.order.payment.refund.execute`  | — (компенсация, лог)   |
| `order.deliver`         | `task.order.deliver.execute`         | `DELIVERED`            |

### Паттерн Worker'а

Каждый listener:
1. Получает сообщение из очереди `task.{topic}.execute`
2. Извлекает `correlationId` из AMQP properties
3. Логирует получение задачи и переменные процесса
4. Обновляет статус заказа в БД (если применимо, по `orderId` из переменных)
5. Формирует результат (пустой `{}` или с доп. переменными)
6. Отправляет результат в exchange `process-engine.tasks` с routing key `task.{topic}.result`, сохраняя `correlationId`

### Формат результата

**Успех:**
```json
{
  "processedAt": "2026-03-30T10:00:00Z"
}
```

С обязательными AMQP properties:
- `correlationId` — из входящего сообщения (без изменений)
- `headers["x-correlation-id"]` — тот же UUID
- `contentType` — `application/json`
- `deliveryMode` — `2` (persistent)

## Автодеплой BPMN-процесса

При старте приложения (`ApplicationRunner`):
1. Читает `order-process.bpmn` из classpath ресурсов
2. Отправляет `POST http://localhost:8080/api/v1/definitions` с файлом как multipart
3. Логирует результат деплоя

## Testing Plan

### Unit Tests
- `OrderService` — маппинг, сохранение, вызов Process Engine
- Каждый Worker — обработка сообщения, обновление статуса, отправка результата

### Integration Tests
- `POST /orders` — end-to-end через MockMvc
- RabbitMQ listeners — через embedded RabbitMQ или Testcontainers

## Open Questions

- Нужна ли авторизация при вызовах к Process Engine (JWT Bearer token)?
- Нужен ли endpoint для получения заказа по ID (`GET /orders/{id}`)?
- Стратегия обработки ошибок при недоступности Process Engine при создании заказа
