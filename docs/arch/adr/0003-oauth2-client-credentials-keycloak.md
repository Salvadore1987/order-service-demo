# ADR-0003: Аутентификация к Process Engine — OAuth2 Client Credentials через Keycloak

## Статус

Принято

## Контекст

REST API Process Engine (`POST /api/v1/instances` и пр.) защищён и требует
аутентификации. Взаимодействие service-to-service, пользовательского
контекста нет.

## Решение

Используем **OAuth2 Client Credentials flow** с Keycloak в роли Identity
Provider:

- realm: `process-engine`, token endpoint:
  `http://localhost:8180/realms/process-engine/protocol/openid-connect/token`;
- клиент: `client-id = order-service`,
  `client-secret = ${KEYCLOAK_CLIENT_SECRET:order-service-secret}`
  (секрет — через переменную окружения);
- собственный компонент `OAuth2TokenProvider`:
  - `getToken()` (synchronized) возвращает закэшированный JWT;
  - обновляет токен заранее — за 10 секунд до истечения `expires_in`;
- `ProcessInstanceClient` подставляет `Authorization: Bearer <token>`
  в каждый запрос к движку.

## Последствия

**Плюсы:**

- Стандартный, проверенный механизм m2m-аутентификации; движок валидирует
  JWT без обращения к Keycloak на каждый запрос.
- Кэширование токена убирает лишние round-trip'ы к Keycloak.
- Секрет не хардкодится — конфигурируется через env.

**Минусы:**

- Ещё одна обязательная runtime-зависимость (Keycloak, :8180).
- Самописный token provider вместо `spring-security-oauth2-client` —
  меньше готовых фич (нет авто-retry, метрик); осознанный выбор ради
  простоты демо.
- `synchronized` на получении токена — потенциальная точка contention при
  высокой конкурентности (приемлемо для текущих нагрузок).
