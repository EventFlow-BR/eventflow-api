# EventFlow architecture

## Runtime and package boundaries

EventFlow is a domain-oriented modular monolith: one Spring Boot application, one PostgreSQL database, and synchronous domain operations inside local JPA transactions. Redis and RabbitMQ are supporting services. The RabbitMQ consumer runs in the same application, not in a separate microservice.

```text
HTTP client
    |
    v
Spring Security: JWT cookie + CSRF
    |
    v
Controllers --> transactional services --> JPA repositories --> PostgreSQL
                    |                         (domain data + processed_messages)
                    |-- event reads --> Spring Cache --> Redis
                    |-- AFTER_COMMIT --> RabbitMQ --> consumer --> processor --> logging handler
                    `-- AFTER_COMMIT --> registration counters

Actuator / Micrometer --> health and Prometheus-format metrics
```

Packages under `src/main/java/br/com/eventflow`:

| Package | Current responsibility |
|---|---|
| `auth` | Registration/login, JWT generation/filtering, cookies, CSRF endpoint, security chain |
| `user` | User entity, roles, persistence |
| `event` | Event lifecycle, visibility, owner operations, cached DTO reads |
| `registration` | Reservation/capacity, expiration scheduler, individual cancellation, events and metrics |
| `registration.messaging` | Message contract, after-commit listeners, publisher/consumer, idempotency, DLQ replay |
| `payment` | Simulated payment approval and atomic registration confirmation |
| `shared` | Technical configuration and common HTTP error handling |
| `ticket` | Placeholder only; no functional ticket flow |

These boundaries are packages, not separate build modules or enforced independent services. Services use repositories across domains when coordinating a transaction: for example, PaymentService updates a Registration and EventService queries active registration counts. The implementation does not claim strict module isolation.

## Transaction and concurrency boundaries

| Operation | Transaction and locks |
|---|---|
| Reserve | JPA transaction; Event pessimistic write lock before availability, duplicate, capacity checks, and insert |
| Pay | JPA transaction; Event lock, then Registration lock; validate after waiting; approve payment and confirm registration together |
| Edit/publish/cancel event | JPA transaction; Event lock before state/ownership checks and mutation |
| Cancel registration | JPA transaction; Registration lock; cancel and refund approved simulated payment together |
| Expire reservations | JPA transaction; pessimistically lock overdue PENDING registrations, then mark EXPIRED |
| Process message | JPA transaction; insert processed-message marker and handle in the same transaction |
| Replay DLQ | RabbitMQ transaction; receive and republish together |

Payment locates the Event with a subquery by registration ID before loading the Registration. This avoids preloading registration state before lock acquisition. Its Event lock is shared with reservation: an uncommitted valid payment cannot lose its capacity when the reservation timestamp passes and another participant requests the last place.

Expiration and individual cancellation do not subsequently acquire an Event lock, so they do not introduce a reverse Registration → Event acquisition order. Locks last until transaction completion, including an outer transaction when present. Payment processing for one event is deliberately serialized to keep v1 capacity reasoning simple.

See [EventRepository](../src/main/java/br/com/eventflow/event/EventRepository.java), [RegistrationRepository](../src/main/java/br/com/eventflow/registration/RegistrationRepository.java), [PaymentService](../src/main/java/br/com/eventflow/payment/PaymentService.java), and the [capacity regression test](../src/test/java/br/com/eventflow/payment/PaymentReservationCapacityConcurrencyIntegrationTest.java).

## Messaging and delivery boundaries

```text
Payment / registration cancellation / expiration
    --> application event inside JPA transaction
    --> database commit
    --> AFTER_COMMIT listener creates RegistrationMessage with UUID
    --> JSON publication to eventflow.events
    --> eventflow.registration.events
    --> RegistrationEventConsumer
    --> RegistrationMessageProcessor (JPA transaction)
    --> processed_messages INSERT ... ON CONFLICT DO NOTHING
    --> RegistrationMessageHandler
```

| Topology | Value |
|---|---|
| Main topic exchange | `eventflow.events` |
| Durable queue | `eventflow.registration.events` |
| Binding | `registration.*` |
| Published routing keys | `registration.confirmed`, `registration.cancelled`, `registration.expired` |
| Dead-letter topic exchange | `eventflow.events.dlx` |
| Durable dead-letter queue | `eventflow.registration.events.dlq` |
| Dead-letter routing key | `registration.dead` |

The JSON converter trusts the registration messaging package. The contract carries message ID, registration/event/participant IDs, event type, and occurrence time. Reservation creation is used by local business metrics; it is not a fourth RabbitMQ publication flow.

Consumer retry is configured for three total attempts, 500 ms initial interval, multiplier 2, and a 2-second cap. Failures after retry are rejected without requeue; the queue's dead-letter configuration routes them to the DLQ. The current handler logs recognized event types and warns about unsupported/invalid types; such warnings do not themselves throw and trigger retry.

A new message ID is inserted into `processed_messages` before handling. A duplicate insert returns zero and processing is skipped. Concurrent deliveries of the same UUID are coordinated by PostgreSQL's unique primary key. If handling throws, the JPA transaction rolls back its marker, enabling another attempt. Deduplication is by message ID, not by registration ID or semantic equivalence.

`RegistrationDeadLetterReprocessor.reprocessNext()` receives one DLQ message and republishes supported event types using `registrationReplayRabbitTemplate` and `rabbitTransactionManager`. It preserves the UUID; a failure rolls the broker transaction back. There is no replay HTTP endpoint, scheduler, or shipped CLI. See the replay integration tests for its invocation and rollback behavior.

**Accepted delivery limitation:** no Outbox or durable recovery of initial publication exists. Database commit and the normal RabbitTemplate send are not atomic, and publisher confirms/returns are not configured. A process/broker failure after commit may leave no delivered message; the DLQ cannot recover a message that never arrived. Consumer deduplication does not turn this into an exactly-once guarantee.

The consumer currently logs events. It does not issue Ticket, send email, or perform an external financial operation. Extending that responsibility would require reconsidering delivery and side-effect guarantees.

Sources: [RabbitMqConfig](../src/main/java/br/com/eventflow/shared/config/RabbitMqConfig.java), [messaging package](../src/main/java/br/com/eventflow/registration/messaging), [application properties](../src/main/resources/application.properties).

## Read cache boundary

`EventCachedReadService.getById()` uses Spring Cache for `EventResponse`, with cache `events`, logical key `eventId`, JSON serialization, and a default TTL of 10 minutes. This is a cache-aside read path backed by PostgreSQL. Listing is not cached.

`EventService.getVisibleEvent()` calls the cached read and then evaluates visibility for the current caller every time. A successful authorization decision is not shared between users. The decision still uses cached attributes, so stale data is bounded by invalidation/TTL rather than a fresh database authorization query.

Successful event update, publication, and cancellation evict the entry through `@CacheEvict`. This is not an atomic PostgreSQL/Redis operation or an explicit AFTER_COMMIT invalidation design. There is no custom Redis-error fallback. Redis stores neither user sessions nor authoritative registration/capacity state.

Sources: [EventCachedReadService](../src/main/java/br/com/eventflow/event/EventCachedReadService.java), [EventService](../src/main/java/br/com/eventflow/event/EventService.java), [CacheConfig](../src/main/java/br/com/eventflow/shared/config/CacheConfig.java).

## Observability boundary

The default configuration exposes only health; the explicit local profile additionally exposes Prometheus. Public health hides component details while checking PostgreSQL, RabbitMQ, and Redis. When enabled, Prometheus output requires application authentication, without a separate operational role. The registry and counters remain active when HTTP metrics exposure is disabled.

`RegistrationMetrics` increments reserved, confirmed, cancelled, and expired counters through AFTER_COMMIT listeners. Rollback does not change those counters in the covered tests. They are process counters, not persisted business totals. No Prometheus server, dashboards, tracing system, or log aggregation service is provisioned.

Sources: [RegistrationMetrics](../src/main/java/br/com/eventflow/registration/RegistrationMetrics.java), [observability tests](../src/test/java/br/com/eventflow/observability).

## Continuous integration boundary

[Backend CI](../.github/workflows/backend-ci.yml) runs a single test job on pushes to `develop`/`main` and pull requests targeting those branches. It uses Temurin Java 21, Maven Wrapper dependency caching, and `./mvnw -B clean test`. PostgreSQL, RabbitMQ, and Redis are started by Testcontainers using Docker on the GitHub-hosted Ubuntu runner. The workflow has read-only repository permissions and performs no deployment.

## Architectural decisions

| Decision | Reason and trade-off |
|---|---|
| Modular monolith | Keeps business transactions and deployment simple; package boundaries are not independently deployed or strictly isolated |
| Pessimistic Event locking | Serializes capacity-sensitive operations; lower per-event write concurrency is accepted |
| Publication AFTER_COMMIT | Prevents messaging about rolled-back business operations; does not close the commit-to-send delivery gap |
| Persisted idempotency | Makes duplicate UUID handling safe across consumer restarts and concurrent deliveries; marker retention has no automated cleanup |
| Redis for read cache only | Avoids making reservation consistency depend on a distributed cache; cache freshness/failure remains a separate concern |
| JWT HttpOnly cookie + CSRF | Keeps JWT out of response bodies/JavaScript while explicitly protecting cookie-authenticated writes |
| Simulated payment | Exercises approval/refund and transaction boundaries without pretending to process real funds |
| Paid events only | Avoids an incomplete free-event branch |
| Reject event cancellation with active registrations | Avoids cascade, bulk refunds, and distributed compensation in v1 |
| No Outbox | Accepts best-effort initial publication for the current logging consumer |

Frontend, Ticket, notification/email, free events, a real gateway, cascade/bulk refunds, Outbox, Grafana, tracing/OpenTelemetry, microservices, Kafka, Kubernetes, and advanced reports are outside v1. They are possible extensions, not a roadmap commitment.
