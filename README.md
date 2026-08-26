# EventFlow API

Backend API for **EventFlow**, a web platform for event management, registrations, payments, and tickets.

EventFlow is being developed as both an academic project and a backend engineering portfolio project. The main goal is to build a complete application while progressively applying backend technologies and concepts commonly used in professional Java environments.

## Project Goals

EventFlow provides a practical environment for studying and applying concepts such as:

* modular monolith architecture;
* authentication and authorization;
* relational database modeling;
* transactional consistency;
* concurrency control;
* asynchronous messaging;
* messaging resilience;
* integration testing;
* caching;
* observability;
* CI/CD;
* containerization;
* distributed systems;
* cloud deployment.

Technologies are introduced progressively and should solve a concrete problem in the system rather than being added only for technical experimentation.

## MVP Scope

### Participants

Participants will be able to:

* create an account;
* authenticate;
* browse published events;
* view event details;
* register for events;
* cancel registrations when allowed;
* view their registrations;
* access issued tickets.

### Organizers

Organizers will be able to:

* create an account;
* authenticate;
* create events;
* edit their own events;
* publish events;
* cancel events;
* define event capacity;
* view registered participants;
* monitor event occupancy.

## Initial Domain

The initial domain contains:

* User
* Event
* Registration
* Payment
* Ticket

### User Roles

* `PARTICIPANT`
* `ORGANIZER`

### Event States

* `DRAFT`
* `PUBLISHED`
* `CANCELLED`
* `FINISHED`

### Registration States

* `PENDING`
* `CONFIRMED`
* `CANCELLED`

### Payment States

* `PENDING`
* `APPROVED`
* `FAILED`
* `EXPIRED`
* `CANCELLED`
* `REFUNDED`

### Ticket States

* `ACTIVE`
* `CANCELLED`

## Architecture

The backend starts as a **modular monolith** implemented as a single Spring Boot application.

The initial domain modules are expected to be:

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

The application is organized by business domain first rather than by global technical layer.

Modules such as `messaging` and `notification` will only be introduced when their corresponding functionality is implemented.

A Notification Service may later be extracted from the modular monolith to provide practical experience with distributed systems and asynchronous communication.

## Planned Technology Stack

### Backend

* Java 21
* Spring Boot
* Spring Web
* Spring Data JPA
* Spring Security
* Bean Validation
* PostgreSQL
* Flyway
* RabbitMQ
* Redis
* JUnit 5
* Mockito
* Testcontainers
* Spring Boot Actuator
* Prometheus
* Grafana
* Docker
* Docker Compose
* GitHub Actions

Cloud infrastructure will be defined during a later project phase.

## Engineering Principles

The project follows these principles:

* start with a modular monolith;
* avoid premature microservices;
* avoid unnecessary abstractions;
* model business rules before implementation;
* keep business rules inside their owning domain;
* use Flyway for database schema evolution;
* protect domain invariants at both application and database levels when appropriate;
* introduce infrastructure only when it solves a concrete problem;
* preserve historical and financial data;
* keep Pull Requests small and reviewable.

## Development Workflow

```text
Issue
↓
Branch
↓
Implementation
↓
Tests
↓
Semantic Commit
↓
Pull Request
↓
Code Review
↓
Merge
```

Branch naming examples:

```text
feature/...
fix/...
test/...
refactor/...
docs/...
chore/...
```

Conventional Commits will be used when appropriate:

```text
feat:
fix:
test:
refactor:
docs:
chore:
```

## Project Roadmap

1. Engineering
2. Foundation
3. Authentication
4. Events
5. Registrations and simulated payments
6. RabbitMQ
7. Messaging resilience
8. Advanced testing with Testcontainers
9. Redis
10. Observability
11. CI/CD
12. Notification Service and real emails
13. Containerized deployment and cloud
14. Kubernetes laboratory and optional Kafka experiment

## Current Status

**Phase 0 — Engineering**

The current phase focuses on defining:

* MVP scope;
* business rules;
* domain lifecycle;
* database model;
* modular architecture;
* security strategy;
* initial project documentation.

Implementation of the Spring Boot application has not started yet.

## Documentation

Detailed technical and domain decisions are maintained in the [`docs`](./docs) directory:

* [Business Rules](./docs/business-rules.md)
* [Architecture](./docs/architecture.md)
* [Database Model](./docs/database-model.md)
* [Security](./docs/security.md)

The documentation evolves together with the implementation and should always describe the actual state of the project.
