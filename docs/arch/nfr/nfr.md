# Нефункциональные требования (NFR) — Order Service

Документ описывает нефункциональные характеристики текущей реализации
order-service: что обеспечено архитектурой уже сейчас, какими механизмами,
и какие ограничения накладывает demo-характер проекта.

## NFR-1. Производительность

| ID | Требование | Реализация / механизм |
|----|-----------|----------------------|
| NFR-1.1 | Создание заказа (`POST /orders`) — короткая синхронная операция | Синхронно выполняются только: INSERT в H2 + один REST-вызов запуска процесса. Вся длительная обработка (валидация, бронирование, оплата, доставка) — асинхронно через external tasks |
| NFR-1.2 | Минимизация обращений к Keycloak | `OAuth2TokenProvider` кэширует JWT и обновляет его лишь за 10 секунд до истечения `expires_in` |
| NFR-1.3 | Независимость шагов процесса | Book Order и Notify Booking исполняются параллельно (BPMN parallel gateway), длительный шаг (book ~5s) не блокирует уведомление |

## NFR-2. Надёжность и отказоустойчивость

| ID | Требование | Реализация / механизм |
|----|-----------|----------------------|
| NFR-2.1 | Повторные попытки оплаты при временных сбоях | Retry-петля в `charge-payment-subprocess`: charge → check status → timer PT5S → charge; счётчик `retryCount` |
| NFR-2.2 | Ограничение числа ретраев | При `retryCount > 5` воркер списания возвращает `context.error(...)` → инцидент в движке, бесконечный цикл исключён |
| NFR-2.3 | Откат побочных эффектов при сбое доставки | BPMN compensation: boundary event на Charge Payment → задача Refund Payment (`order.payment.refund`) |
| NFR-2.4 | Различие бизнес- и технических ошибок | Бизнес-неудача оплаты (`PaymentChargeException`) → `complete(isPaymentSuccess=false)` и альтернативная ветка процесса; технические ошибки → `context.error(code, message)` |
| NFR-2.5 | Сохранность состояния процесса | Состояние order-process персистентно во внешнем Process Engine — рестарт order-service не теряет запущенные процессы |
| NFR-2.6 | Идемпотентность воркеров | Задачи могут доставляться повторно (at-least-once через MQ); обработчики должны переживать повтор (статусные апдейты идемпотентны, `processedAt` фиксирует факт обработки) |

## NFR-3. Безопасность

| ID | Требование | Реализация / механизм |
|----|-----------|----------------------|
| NFR-3.1 | Аутентификация service-to-service к Process Engine | OAuth2 Client Credentials через Keycloak (realm `process-engine`, client `order-service`); каждый запрос — `Authorization: Bearer <JWT>` |
| NFR-3.2 | Секреты не в коде | `client-secret` берётся из переменной окружения `KEYCLOAK_CLIENT_SECRET` (default только для локального окружения) |
| NFR-3.3 | Ограничение (demo) | Сам эндпоинт `POST /orders` не защищён (нет Spring Security); H2 console включена — допустимо только для dev |

## NFR-4. Масштабируемость

| ID | Требование | Реализация / механизм |
|----|-----------|----------------------|
| NFR-4.1 | Горизонтальное масштабирование обработки | Воркеры конкурентно потребляют задачи из RabbitMQ — можно запустить несколько инстансов сервиса, задачи распределятся по консьюмерам |
| NFR-4.2 | Сглаживание пиков | Очередь буферизует external tasks; Process Engine не ждёт воркеров синхронно |
| NFR-4.3 | Ограничение (demo) | H2 in-memory не разделяется между инстансами — реальное масштабирование требует внешней СУБД (см. [ADR-0005](../adr/0005-h2-in-memory-database.md)) |

## NFR-5. Наблюдаемость

| ID | Требование | Реализация / механизм |
|----|-----------|----------------------|
| NFR-5.1 | Трассировка HTTP-вызовов к движку | `RestClientLoggingInterceptor` логирует метод, URI, тело запроса и ответа (через `BufferingClientHttpRequestFactory`) |
| NFR-5.2 | Корреляция логов с процессом | Каждый воркер логирует correlation id и переменные задачи; сквозной ключ — businessKey = orderId |
| NFR-5.3 | Отладка БД | `show-sql: true`, H2 console (`/h2-console`) — только dev |

## NFR-6. Сопровождаемость и тестируемость

| ID | Требование | Реализация / механизм |
|----|-----------|----------------------|
| NFR-6.1 | Процесс визуализируем и редактируем | BPMN-схемы в `src/main/resources/bpmn/`, совместимы с Camunda Modeler 5.31.0; auto-deploy при старте |
| NFR-6.2 | Изоляция слоёв | controller / service / repository / client / worker — отдельные пакеты; constructor injection |
| NFR-6.3 | Двухуровневое тестирование | `OrderControllerIntegrationTest` (Spring-контекст, движок замокан) и `OrderProcessIntegrationTest` (живая инфраструктура: engine + Keycloak + RabbitMQ; happy path и compensation flow) |

## NFR-7. Ограничения окружения (текущая конфигурация)

| Параметр | Значение |
|----------|----------|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Порт сервиса | 8081 |
| Process Engine | http://localhost:8080 |
| Keycloak | http://localhost:8180 (realm `process-engine`) |
| RabbitMQ | localhost:5672 (guest/guest, topicPrefix `order-service`) |
| Redis | `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` |
| БД | H2 in-memory `jdbc:h2:mem:orderdb`, `ddl-auto: create-drop` |
