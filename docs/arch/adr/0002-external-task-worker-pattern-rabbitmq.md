# ADR-0002: External Task Worker pattern через worker-spring-boot-starter и RabbitMQ

## Статус

Принято

## Контекст

Шаги BPMN-процесса должна исполнять бизнес-логика Order Service. Варианты:
встроенные Java-делегаты внутри движка (жёсткая связность, деплой логики
в движок) или паттерн **external task** — движок публикует задачи, внешние
воркеры их разбирают и возвращают результат.

## Решение

Используем external task pattern с доставкой задач через **RabbitMQ**
и библиотеку `uz.salvadore:worker-spring-boot-starter:1.0-SNAPSHOT`:

- каждый шаг процесса — отдельный класс-обработчик, реализующий
  `ExternalTaskHandler` и аннотированный `@JobWorker(topic = "...")`;
- топики (7 шт.): `order.validate`, `order.book`, `order.notify`,
  `order.payment.charge`, `order.payment.status`, `order.payment.refund`,
  `order.deliver`;
- результат возвращается через `TaskContext.complete(variables)`
  (успех/бизнес-результат) или `TaskContext.error(code, message)`
  (техническая ошибка);
- транспорт: RabbitMQ `localhost:5672` (guest/guest),
  `topicPrefix: order-service` (конфигурация `process-engine.worker.rabbitmq`
  в `application.yml`).

Контракты сообщений описаны в
[`../integrations/async/asyncapi.yaml`](../integrations/async/asyncapi.yaml).

## Последствия

**Плюсы:**

- Полная развязка движка и бизнес-логики: воркеры — обычные Spring-бины,
  тестируются и деплоятся вместе с сервисом.
- Горизонтальное масштабирование: можно поднять несколько инстансов
  сервиса — задачи распределятся по консьюмерам очередей.
- Очередь сглаживает пики и переживает кратковременную недоступность воркеров.
- Чёткое разделение бизнес-ошибок (`complete` с `isPaymentSuccess=false`,
  процесс выбирает альтернативную ветку) и технических (`error` → инцидент).

**Минусы:**

- Дополнительная инфраструктура (RabbitMQ) обязательна для работы процесса.
- Зависимость от внутренней библиотеки `worker-spring-boot-starter`
  (SNAPSHOT-версия — нужен локальный/корпоративный maven-репозиторий).
- Асинхронность усложняет отладку — нужна корреляция по correlation id /
  businessKey (логируется в каждом обработчике).
