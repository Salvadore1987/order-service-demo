# Архитектурная документация — Order Service

Архитектурное описание сервиса **order-service** — Spring Boot 3.4.4
(Java 21) микросервиса заказов, оркеструющего жизненный цикл заказа через
внешний BPMN Process Engine (external task pattern, RabbitMQ, Keycloak).

## Состав

### C4-модель (PlantUML)

| Уровень | Файл | Что показывает |
|---------|------|----------------|
| Context | [c4/c4-context.puml](c4/c4-context.puml) | Сервис и его окружение: клиент, Process Engine, Keycloak, RabbitMQ, Redis |
| Container | [c4/c4-container.puml](c4/c4-container.puml) | REST API, воркеры, H2 и внешняя инфраструктура с портами/протоколами |
| Component | [c4/c4-component.puml](c4/c4-component.puml) | Внутренние компоненты: controller, service, repository, client, token provider, 7 task-handler'ов |

### UML (PlantUML)

| Диаграмма | Файл | Что показывает |
|-----------|------|----------------|
| Class | [uml/class-diagram.puml](uml/class-diagram.puml) | Доменная модель (Order, DeliveryAddress, OrderStatus), DTO, сервисы, воркеры и их связи |
| ER | [uml/er-diagram.puml](uml/er-diagram.puml) | Таблица `orders` (H2) и логическая связь с process instance во внешнем движке |
| Sequence | [uml/sequence-create-order.puml](uml/sequence-create-order.puml) | Полный поток: `POST /orders` → запуск процесса → асинхронное исполнение шагов, retry оплаты, компенсация |

Рендеринг: `plantuml docs/arch/**/*.puml` или любой PlantUML-плагин
(IntelliJ IDEA / VS Code). C4-диаграммы используют стандартную stdlib
(`!include <C4/...>`).

### Интеграции

| Тип | Файл | Содержание |
|-----|------|-----------|
| Sync (REST) | [integrations/sync/openapi.yaml](integrations/sync/openapi.yaml) | OpenAPI 3.0.3: `POST /orders` (схемы CreateOrderRequest/Response) |
| Async (External Tasks) | [integrations/async/asyncapi.yaml](integrations/async/asyncapi.yaml) | AsyncAPI 2.6.0: 7 топиков `order.*` через RabbitMQ, payload-схемы переменных процесса |

### ADR (Architecture Decision Records)

| № | Решение |
|---|---------|
| [ADR-0001](adr/0001-external-process-engine-orchestration.md) | Оркестрация жизненного цикла заказа через внешний BPMN Process Engine |
| [ADR-0002](adr/0002-external-task-worker-pattern-rabbitmq.md) | External Task Worker pattern через worker-spring-boot-starter и RabbitMQ |
| [ADR-0003](adr/0003-oauth2-client-credentials-keycloak.md) | OAuth2 Client Credentials через Keycloak |
| [ADR-0004](adr/0004-compensation-for-payment-refund.md) | Компенсация платежа (BPMN compensation) и retry-сабпроцесс оплаты |
| [ADR-0005](adr/0005-h2-in-memory-database.md) | H2 in-memory как demo-БД |

### Требования и сценарии

| Документ | Файл |
|----------|------|
| Нефункциональные требования (NFR) | [nfr/nfr.md](nfr/nfr.md) |
| Сценарии использования (Use Cases) | [use-cases/use-cases.md](use-cases/use-cases.md) |

## Краткая сводка системы

- **REST API**: `POST /orders` (:8081) — создание заказа, запуск процесса
  `order-process` (businessKey = orderId).
- **BPMN**: `order-process` (validate → parallel book/notify → charge
  payment → deliver, compensation refund) + `charge-payment-subprocess`
  (retry-петля charge → status → timer PT5S).
- **Воркеры** (топики): `order.validate`, `order.book`, `order.notify`,
  `order.payment.charge`, `order.payment.status`, `order.payment.refund`,
  `order.deliver`.
- **Статусы заказа**: CREATED → VALIDATED → BOOKED → PAID → DELIVERED.
- **Инфраструктура**: Process Engine :8080, Keycloak :8180 (realm
  `process-engine`), RabbitMQ :5672, Redis :6379, H2 in-memory.
