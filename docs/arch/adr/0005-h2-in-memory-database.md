# ADR-0005: H2 in-memory в качестве БД (demo-решение)

## Статус

Принято (с ограничением: только demo/dev; для production подлежит пересмотру)

## Контекст

Сервису нужна реляционная БД для хранения заказов (таблица `orders`).
Проект — демонстрационный: важна простота запуска «одной командой»,
без внешней СУБД.

## Решение

- **H2 in-memory**: `jdbc:h2:mem:orderdb` (sa, без пароля);
- схема генерируется Hibernate: `spring.jpa.hibernate.ddl-auto: create-drop`;
- `show-sql: true` и H2 console (`spring.h2.console.enabled: true`) —
  для отладки;
- доменная модель: `Order` (JPA entity, PK — UUID, генерируется в коде)
  + `DeliveryAddress` (`@Embeddable`, колонки в той же таблице);
- доступ через Spring Data JPA (`OrderRepository extends
  JpaRepository<Order, UUID>`).

## Последствия

**Плюсы:**

- Нулевая инфраструктура для БД: сервис и тесты запускаются без Docker/СУБД.
- create-drop гарантирует чистое состояние на каждый запуск —
  предсказуемые демо и тесты.

**Минусы / ограничения:**

- **Данные теряются при рестарте** — все заказы пропадают, хотя их
  process instances продолжают жить в Process Engine (рассинхронизация).
- Не пригодно для production: нет durability, конкурентного доступа
  с нескольких инстансов, бэкапов.
- При миграции на PostgreSQL и т.п. потребуется заменить `ddl-auto`
  на миграции (Flyway/Liquibase) и проверить маппинг типов
  (UUID, NUMERIC).

Связано с [ADR-0001](0001-external-process-engine-orchestration.md) —
состояние процесса при этом персистентно в движке.
