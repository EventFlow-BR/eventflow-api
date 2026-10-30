# EventFlow API

## Overview

EventFlow is a Java backend for event management and paid reservations. It is a backend portfolio project focused on security, transactional consistency, concurrency, asynchronous messaging, resilience, caching, integration testing, and observability.

The v1 scope is backend-only. The default configuration accepts external infrastructure settings and exposes only health over HTTP. Public deployment and deployment smoke testing remain separate steps; this repository is not presented as a production-validated service.

## Highlights

- Temporary reservations with pessimistic locking and protection of the last available place.
- Simulated payment and registration confirmation in one database transaction.
- JWT in an HttpOnly cookie, with a real HTTP CSRF-token flow.
- RabbitMQ after-commit publication, retry, DLQ, transactional replay, and persisted idempotency.
- Redis read caching with visibility checks outside the cached method.
- Integration and concurrency tests against PostgreSQL, RabbitMQ, and Redis containers.

## Architecture

A **domain-oriented modular monolith**: one Spring Boot application and one deployable backend unit. The consumer runs inside the same application.

```text
Client
  |
  v
Spring Boot API
  |-- PostgreSQL: domain data, migrations, processed message IDs
  |-- Redis: event read cache
  |-- RabbitMQ --> Registration consumer (same application)
  `-- Actuator / Micrometer --> health and Prometheus-format metrics
```

Packages are organized by domain: `auth`, `user`, `event`, `registration`, and `payment`, with technical configuration in `shared`. `ticket` is only a placeholder, not a functional module. See [architecture and trade-offs](docs/architecture.md).

## Tech stack

| Area | Technologies used |
|---|---|
| Runtime and build | Java 21, Spring Boot 4.0.8, Maven Wrapper 3.9.16 |
| API and security | Spring MVC, Bean Validation, Spring Security, BCrypt, JWT/JJWT 0.12.7 |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL, Flyway |
| Messaging and cache | RabbitMQ, Spring AMQP, Redis, Spring Cache, JSON serialization |
| Testing | JUnit Jupiter, Mockito, MockMvc, Spring Boot Test, Testcontainers |
| Operations | Docker Compose, Spring Boot Actuator, Micrometer, Prometheus registry |

Compose and Testcontainers use PostgreSQL 17, RabbitMQ 4-management, and Redis 7.4-alpine. A Prometheus server, Grafana, and a frontend are not included.

## Features / v1 scope

- Account registration, login, current-user lookup, and logout.
- `ORGANIZER` and `PARTICIPANT` roles with resource ownership checks.
- Paid event creation, draft editing, publication, and constrained cancellation.
- Temporary reservations, capacity checks, payment simulation, confirmation, individual cancellation/refund simulation, and expiration.
- Messaging resilience, event read caching, and technical/business metrics.

## Core business flows

### Event lifecycle

Events start as `DRAFT`; price must be greater than zero. Only the owner can edit, and only while the event is a draft. Publication requires `DRAFT` and a future start date. Published events cannot be edited.

DRAFT or PUBLISHED events can be cancelled by their owner before the start, only when there are no active registrations. `FINISHED` is a reserved state with no automatic transition in v1.

Event reads require authentication. Participants list PUBLISHED events; organizers list their own events. A published event can be fetched by ID by either role; other states are visible only to the organizer owner. A cancelled event is not kept publicly visible based on its publication history.

### Reservation

A participant reserves a place in a PUBLISHED event that has not started. The registration starts as `PENDING`, with a default deadline of **15 minutes**. A participant cannot have another active registration for that event.

```text
occupied places = CONFIRMED + PENDING whose reservation deadline is still in the future
```

### Payment

Payment is **simulated for domain and transaction modeling**; no money is collected.

```text
PENDING registration
  --> Event lock --> Registration lock --> eligibility validation
  --> Payment APPROVED + Registration CONFIRMED
  --> database commit --> AFTER_COMMIT listeners
```

The caller must own the registration. Its reservation must still be valid, the event must be PUBLISHED and not started, and no payment may already exist for that registration. Rejected payment does not confirm the registration or create a payment.

### Cancellation / expiration

Participants can cancel their own valid PENDING or CONFIRMED registrations before the event starts. Confirmed cancellation also changes its approved simulated payment to REFUNDED in the same transaction.

The scheduler changes overdue PENDING registrations to EXPIRED, checking every **60 seconds** by default. A reservation stops occupying capacity at its deadline, even before the scheduler updates its status. No pending Payment is created during reservation.

Event cancellation does not cascade to registrations or generate bulk refunds; active registrations prevent it. See [business rules](docs/business-rules.md).

## Concurrency

Reservation acquires a pessimistic write lock on the Event before checking capacity. Payment uses the same Event lock, then locks the Registration. Event editing, publication, and cancellation also lock the Event.

This prevents a reservation from expiring logically while its valid payment is still uncommitted and another participant takes the last place. The competing reservation waits for the payment transaction, then sees the confirmed registration. Tests exercise this case and concurrent last-place reservation, duplicate payment, cancellation, and expiration.

## Security

Passwords are hashed with BCrypt. Authentication uses JWT in the `eventflow_token` cookie: HttpOnly, SameSite=Lax, Path=/, with configurable Secure. There is no Bearer-header authentication.

```text
register/login --> JWT HttpOnly cookie --> GET /api/auth/csrf
  --> JSON CSRF token + separate XSRF-TOKEN cookie
  --> POST/PUT/logout with both cookies and X-XSRF-TOKEN header
```

CSRF remains enabled. Register/login are exempt; logout is not. Missing or invalid CSRF produces 403 on protected writes. The CSRF cookie is also HttpOnly: use the token returned in JSON, not the cookie contents. See the [HTTP walkthrough and authorization details](docs/security.md).

## Messaging and resilience

```text
business transaction --> commit --> AFTER_COMMIT --> RabbitMQ
  --> consumer --> persisted idempotency check --> logging handler
```

| Element | Name |
|---|---|
| Topic exchange | `eventflow.events` |
| Durable queue / binding | `eventflow.registration.events` / `registration.*` |
| Routing keys | `registration.confirmed`, `registration.cancelled`, `registration.expired` |
| Dead-letter exchange | `eventflow.events.dlx` |
| Durable DLQ / routing key | `eventflow.registration.events.dlq` / `registration.dead` |

Messages use JSON. Consumer retry allows **3 total attempts**, with 500 ms initial backoff, multiplier 2, capped at 2 seconds; exhausted failures are rejected without requeue and routed to the DLQ. The processor records message IDs in `processed_messages` in the same JPA transaction as handling. Duplicate IDs are skipped; handler failure rolls back the marker, allowing retry.

`RegistrationDeadLetterReprocessor.reprocessNext()` performs receive/republish in a RabbitMQ transaction and preserves the message ID. It is an application service, not an exposed HTTP endpoint or shipped CLI.

The current handler logs events; it does not issue tickets or send emails. There is **no Outbox, durable recovery of initial publication after commit, or exactly-once guarantee**. See [delivery boundaries](docs/architecture.md#messaging-and-delivery-boundaries).

## Redis cache

Spring Cache provides cache-aside reads of `EventResponse` in cache `events`, keyed by `eventId`, using JSON and a default **10-minute TTL**. Successful update, publish, and cancel operations evict the corresponding entry.

`EventCachedReadService` caches data; `EventService` checks visibility on every request, including cache hits. Authorization is not cached for a particular caller. Redis is neither the primary database nor a session store. Cached attributes can still be stale until eviction/TTL, and there is no explicit Redis-failure fallback.

## Observability

The default configuration exposes only public `/actuator/health`, with overall status and no component details. Health contributors cover PostgreSQL, RabbitMQ, and Redis. The explicit `local` profile additionally exposes authenticated `/actuator/prometheus` for Micrometer JVM, process/system, and business metrics. The Prometheus registry and business counters remain active in both configurations.

```text
eventflow.registration.reserved
eventflow.registration.confirmed
eventflow.registration.cancelled
eventflow.registration.expired
```

Business counters increment through AFTER_COMMIT listeners; rollback does not increment them. Counters reset with the process. The registry exports metrics; no scraper or dashboards are provisioned here.

## Testing

The latest complete production-configuration run, verified from Surefire reports for the current backend code, completed **246 tests: 0 failures, 0 errors, 0 skipped**. This is a recorded result, not a coverage percentage or a promise about future runs.

```bash
./mvnw clean test
```

Docker must be running: Spring Boot integration tests use PostgreSQL, RabbitMQ, and Redis Testcontainers. Tests cover services, HTTP/MockMvc, real CSRF acquisition, concurrency, transaction rollback, after-commit events, retry/DLQ/replay, idempotency, cache/TTL/visibility, health, and metrics. Messaging tests explicitly enable consumers where required; other integration tests keep them disabled to inspect queues.

### Continuous integration

[Backend CI](.github/workflows/backend-ci.yml) runs on pushes to `develop` and `main`, and on pull requests targeting either branch. One `ubuntu-latest` job uses Temurin Java 21, the Maven Wrapper, and Maven dependency caching to execute `./mvnw -B clean test`. Testcontainers uses the runner's Docker to start PostgreSQL, RabbitMQ, and Redis; no Compose services or production secrets are required. The workflow runs tests only, with read-only repository permissions and no deployment.

## API endpoints

`O` = organizer; `P` = participant; `A` = authenticated. All mutating endpoints below require CSRF except register/login. Ownership and business-state rules apply in addition to access rules.

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register with an application role |
| POST | `/api/auth/login` | Public | Authenticate and set JWT cookie |
| GET | `/api/auth/me` | A | Current user |
| GET | `/api/auth/csrf` | A | Obtain CSRF token and cookie |
| POST | `/api/auth/logout` | CSRF; permitAll | Clear JWT cookie |
| POST | `/api/events` | O | Create draft |
| GET | `/api/events` | A | List events according to role |
| GET | `/api/events/{eventId}` | A + visibility | Read event |
| PUT | `/api/events/{eventId}` | O + owner | Edit draft |
| POST | `/api/events/{eventId}/publish` | O + owner | Publish future draft |
| POST | `/api/events/{eventId}/cancel` | O + owner | Cancel without active registrations |
| POST | `/api/events/{eventId}/registrations` | P | Reserve a place |
| POST | `/api/registrations/{registrationId}/payment` | P + owner | Simulate payment and confirm |
| POST | `/api/registrations/{registrationId}/cancel` | P + owner | Cancel registration/refund when applicable |
| GET | `/actuator/health` | Public | Overall health |
| GET | `/actuator/prometheus` | A; local profile | Prometheus-format metrics; not exposed by default |

There are no registration-list, participant-list, occupancy, ticket, or refund-only endpoints.

## Running locally

Prerequisites: a full **JDK 21** (`java` and `javac`, with `JAVA_HOME` pointing to the JDK), Docker, and Docker Compose v2. Maven is supplied by the wrapper. Initial dependency/image downloads require network access.

```bash
git clone --branch main https://github.com/EventFlow-BR/eventflow-api.git
cd eventflow-api
docker compose up -d
docker compose ps
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```

Wait for PostgreSQL/Redis to be healthy and for the RabbitMQ diagnostic to succeed before starting the API. RabbitMQ has no Compose healthcheck. The API runs on the host, not in Compose:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

From another terminal, once startup and Flyway migration complete:

```bash
curl -fsS http://localhost:8080/actuator/health
```

A healthy response includes `"status":"UP"` without component details; probe group names may also be present. Read [security.md](docs/security.md#local-http-walkthrough) for login and protected requests. On Windows use `mvnw.cmd` instead of `./mvnw`.

Local service ports are PostgreSQL **5433**, RabbitMQ **5672** (management **15672**), and Redis **6379**. PostgreSQL and RabbitMQ use local database/user credentials configured in `compose.yaml`; Redis has no local password. Stop the API with Ctrl+C and infrastructure with `docker compose down`; named PostgreSQL/RabbitMQ volumes remain.

## Configuration

Configuration comes from Spring properties, exported environment variables, or IDE run settings. The application does not load a `.env` file itself.

| Variable / property | Current default or behavior |
|---|---|
| `JWT_SECRET` | Required outside `local`; local has a known development-only fallback |
| `JWT_EXPIRATION` | `3600000` milliseconds |
| `AUTH_COOKIE_SECURE` | `true`; `false` under `local`, for both JWT and CSRF cookies |
| `REGISTRATION_RESERVATION_TIMEOUT_MINUTES` | `15` |
| `REGISTRATION_EXPIRATION_CHECK_INTERVAL_MS` | `60000`, fixed delay |
| `app.auth.cookie-name` | `eventflow_token` |
| `spring.cache.redis.time-to-live` | Event cache defaults to `10m` |
| `app.rabbitmq.registration-consumer.enabled` | Enabled when absent; `false` disables the consumer |

### Local development

Aliases below are defined **only in `application-local.properties`**:

| Local alias | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/eventflow` |
| `DB_USERNAME`, `DB_PASSWORD` | `eventflow` / `eventflow` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | `eventflow` / `eventflow` |

Redis uses Spring Boot's localhost:6379 defaults. The local profile matches the Compose services. Its JWT fallback, credentials, non-Secure cookies, and published infrastructure ports are for development only.

### Production / deployment configuration

`application.properties` is the production-like default: no local profile is activated automatically, JWT has no fallback, cookies default to Secure, and only health is exposed. `application-local.properties` supplies development overrides. A separate `prod` profile is unnecessary.

Use exported environment variables with the same executable JAR. Do not activate `local` for deployment:

| Variable | Required for deployment | Purpose |
|---|---|---|
| `SERVER_PORT` | No; default 8080 | HTTP port selected by the platform/operator |
| `SPRING_DATASOURCE_URL` | Yes | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes for password-authenticated DB | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Yes for password-authenticated DB | Database password |
| `SPRING_RABBITMQ_HOST` | Yes for external broker | Broker hostname/address |
| `SPRING_RABBITMQ_PORT` | No; default 5672 | Broker port |
| `SPRING_RABBITMQ_USERNAME` | Yes for external broker | Broker user |
| `SPRING_RABBITMQ_PASSWORD` | Yes for external broker | Broker password |
| `SPRING_RABBITMQ_VIRTUAL_HOST` | No; default `/` | Broker virtual host |
| `SPRING_DATA_REDIS_HOST` | Yes for external Redis | Redis hostname/address |
| `SPRING_DATA_REDIS_PORT` | No; default 6379 | Redis port |
| `SPRING_DATA_REDIS_PASSWORD` | When Redis requires authentication | Redis password |
| `JWT_SECRET` | Yes | Private random HMAC key of at least 32 UTF-8 bytes; no non-local fallback |
| `AUTH_COOKIE_SECURE` | No; defaults to `true` | Keep true for JWT and CSRF cookies over HTTPS |

After supplying these settings securely, start the packaged application with `java -jar target/eventflow-api-0.0.1-SNAPSHOT.jar`. `JWT_EXPIRATION` and reservation/cache settings remain available as documented above. The application fails at startup if the required JWT secret is absent.

There is no custom conversion from a provider's `DATABASE_URL` to JDBC or from `PORT` to `server.port`: supply a JDBC URL and `SERVER_PORT`. Flyway remains enabled by default and applies migrations at startup; Hibernate uses `ddl-auto=none` and open-in-view is disabled.

Local and default configurations have been startup-validated against disposable/local services; this is not evidence of public deployment or production TLS readiness. Configure service TLS, HTTPS termination, network restrictions, and secrets for the chosen provider separately. Driver connection/pool defaults are retained; timeout tuning remains an operational decision rather than a set of arbitrary application overrides.

## Architectural decisions

A modular monolith keeps transactional workflows in one process. Pessimistic Event locking protects capacity; persisted message IDs protect against duplicate delivery. RabbitMQ publication follows database commit, with its delivery gap accepted for the current logging handler. Redis is limited to read caching. Cookie JWT authentication retains CSRF protection. Payments are simulated, events are paid-only, and event cancellation rejects active registrations instead of introducing a cascade. See [the detailed decisions](docs/architecture.md#architectural-decisions).

## Current limitations

- Logout clears the cookie but does not revoke a copied JWT; no refresh-token flow exists.
- Initial after-commit publication has no durable recovery; replay is a service method without an operational endpoint/CLI.
- Cache and database changes are not one atomic transaction; there is no explicit Redis-failure fallback.
- Prometheus is exposed only by the local profile by default. If explicitly re-enabled elsewhere, its existing security rule permits any authenticated application user; there is no dedicated operations role.
- No automatic FINISHED transition, pagination, registration-query endpoints, or functional ticket module exists.
- GitHub Actions CI runs the test suite; no API Dockerfile, CD pipeline, production deployment, or deployment smoke-test evidence is included. The Maven artifact version remains `0.0.1-SNAPSHOT`.

## Out of scope / future work

Frontend, Ticket, Notification Service/email, free events, a payment gateway, event-cancellation cascade/bulk refunds, Outbox, Grafana, tracing/OpenTelemetry/correlation IDs, microservices, Kafka, Kubernetes, and advanced queries/reports are outside v1. These are possible future extensions, not commitments for the current version.

## Further documentation

- [Architecture and decisions](docs/architecture.md)
- [Business rules](docs/business-rules.md)
- [Database model and migration history](docs/database-model.md)
- [Security and HTTP walkthrough](docs/security.md)
