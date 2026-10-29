# EventFlow Architecture

## Architectural Style

EventFlow starts as a **modular monolith**.

The backend will initially be implemented as:

* one Spring Boot application;
* one codebase;
* one deployable backend unit.

The goal is to maintain clear business boundaries without introducing distributed-system complexity before it is justified.

## Package Organization

The application will be organized primarily by business domain.

Initial structure:

```text
eventflow
├── auth
├── user
├── event
├── registration
├── payment
├── ticket
└── shared
```

Technical layers may exist inside each domain module when necessary.

Example:

```text
event
├── controller
├── service
├── repository
├── dto
├── mapper
├── entity
└── exception
```

Subpackages should only be created when corresponding implementation code exists.

Empty architectural placeholder packages should be avoided.

## Module Responsibilities

### auth

Responsible for authentication-related application flows, including:

* account registration entry point;
* login;
* logout;
* authenticated user retrieval;
* Spring Security integration;
* authentication cookie handling.

The `auth` module does not own the User domain entity.

### user

Owns:

* User;
* User role;
* user persistence;
* user lookup;
* identity-related domain data.

### event

Owns:

* Event;
* Event status;
* Event lifecycle;
* Event creation;
* Event editing;
* Event publication;
* Event cancellation;
* public Event queries;
* Organizer Event queries.

### registration

Owns:

* Registration;
* Registration lifecycle;
* duplicate Registration rules;
* Event capacity reservation;
* reservation expiration;
* participant cancellation;
* Registration queries.

### payment

Owns:

* Payment;
* Payment status;
* simulated payment processing;
* approval;
* failure;
* expiration;
* cancellation;
* refund behavior.

### ticket

Owns:

* Ticket;
* Ticket status;
* Ticket issuance;
* Ticket cancellation;
* Participant Ticket queries;
* public Ticket identification.

### shared

Contains only genuinely cross-cutting concerns.

Examples may include:

* global HTTP error representation;
* shared technical configuration;
* cross-cutting infrastructure components.

Domain-specific behavior must not be moved into `shared`.

The project should avoid premature generic abstractions such as:

* `BaseService`;
* `BaseRepository`;
* `BaseEntity`;
* generic mapper hierarchies;
* generic utility layers without concrete value.

## Domain Ownership

Each module owns its business rules and persistence.

A module should avoid directly modifying another module through that module's Repository.

For example, Payment processing should request Registration confirmation through Registration behavior instead of modifying Registration persistence directly.

The principle is:

> Business rules remain in the module that owns the domain concept.

## Cross-Module Collaboration

Cross-module communication should:

* minimize coupling;
* preserve domain ownership;
* avoid unnecessary direct Repository access;
* avoid circular dependencies.

Exact collaboration mechanisms will be defined when concrete use cases are implemented.

Premature interfaces or abstraction layers should not be created only to simulate isolation.

## Persistence Boundaries

Each module manages persistence associated with its own entities.

Cross-module Repository access should be avoided unless a concrete technical reason justifies it.

The initial implementation may use synchronous service collaboration inside the monolith.

Asynchronous communication will be introduced later only when RabbitMQ solves a concrete system problem.

## JPA Relationships

The persistence model should avoid unnecessarily large bidirectional entity graphs.

Relationships will be defined based on actual navigation requirements.

The project should avoid introducing JPA relationships only for convenience when they create:

* unnecessary coupling;
* circular serialization;
* unexpected lazy loading;
* N+1 query problems;
* dangerous cascading behavior.

## Deferred Modules

### messaging

The `messaging` module will not exist until RabbitMQ is introduced.

At that point it may contain responsibilities such as:

* message contracts;
* producers;
* consumers;
* exchange and queue configuration;
* routing configuration;
* serialization.

### notification

Notification behavior will only be introduced when notification use cases are implemented.

Notification may initially exist as part of the modular monolith.

A later project phase may extract it into a dedicated `notification-service`.

## Microservice Evolution

No initial domain module will be extracted into a microservice.

The system must first operate correctly as a modular monolith.

A future Notification Service extraction will be used to study concepts such as:

* asynchronous communication;
* service boundaries;
* partial failures;
* retries;
* idempotency;
* distributed observability;
* eventual consistency.

## Future Architecture Evolution

Planned learning areas include:

* RabbitMQ;
* asynchronous domain events;
* messaging resilience;
* Redis;
* Testcontainers;
* observability;
* CI/CD;
* Notification Service extraction;
* cloud deployment;
* Kubernetes;
* optional Kafka experimentation.

These technologies are not part of the initial architecture until their corresponding phase begins.

## Architecture Principles

EventFlow follows these principles:

1. Prefer simple solutions until complexity is justified.
2. Keep domain rules close to the domain that owns them.
3. Avoid premature microservices.
4. Avoid circular module dependencies.
5. Avoid cross-module Repository access when possible.
6. Keep `shared` small.
7. Avoid abstractions without multiple concrete needs.
8. Let architecture evolve when real system problems appear.
