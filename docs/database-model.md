# EventFlow Initial Database Model

## Overview

EventFlow uses PostgreSQL as its relational database.

The initial MVP contains five main tables:

* `users`
* `events`
* `registrations`
* `payments`
* `tickets`

The database schema will be managed exclusively through Flyway migrations.

Hibernate automatic schema modification must not be used as the production schema management strategy.

## Relationship Overview

```text
User (Organizer)
      1
      │
      N
    Event
      1
      │
      N
 Registration
      N
      │
      1
User (Participant)

Registration
      1
      │
     0..1
   Payment

Registration
      1
      │
     0..1
    Ticket
```

## users

Initial fields:

```text
user_id
name
email
password_hash
role
created_at
updated_at
```

### Rules

* `user_id` is the internal primary key.
* Internal primary keys initially use database-generated numeric identifiers.
* `email` is required and unique.
* `password_hash` is required.
* passwords are never stored in plain text.
* `role` is required.

Initial roles:

```text
PARTICIPANT
ORGANIZER
```

Application code will normalize email consistently before persistence.

## events

Initial fields:

```text
event_id
organizer_id
name
description
location
start_date
end_date
capacity
price
status
published_at
created_at
updated_at
```

### Relationships

`organizer_id` references `users.user_id`.

Each Event belongs to exactly one Organizer.

The database foreign key guarantees that the User exists.

The application is responsible for verifying that the referenced User has the `ORGANIZER` role.

### Event Constraints

The initial model requires:

```text
capacity > 0
price >= 0
start_date < end_date
```

The database should not enforce permanent constraints such as:

```text
start_date > current time
```

because an Event that is valid today will naturally have a start date in the past after it occurs.

Current-time validation belongs to creation and publication business operations.

### Event Completeness

Every persisted Event must contain:

* name;
* description;
* location;
* start date;
* end date;
* capacity;
* price;
* organizer;
* status.

A `DRAFT` Event is a complete persisted Event that has not yet been published.

### published_at

`published_at` is nullable.

When an Event is first published, it records the publication timestamp.

This allows the system to distinguish:

```text
DRAFT → CANCELLED
```

from:

```text
DRAFT → PUBLISHED → CANCELLED
```

The second Event remains publicly visible because it was previously published.

## registrations

Initial fields:

```text
registration_id
participant_id
event_id
status
reservation_expires_at
created_at
updated_at
```

### Relationships

`participant_id` references `users.user_id`.

`event_id` references `events.event_id`.

Each Registration belongs to exactly:

* one Participant;
* one Event.

### Duplicate Registration

A Participant cannot have more than one active Registration for the same Event.

Active registrations are:

* `PENDING`;
* `CONFIRMED`.

Historical `CANCELLED` registrations must remain possible.

Therefore a simple constraint such as:

```sql
UNIQUE (participant_id, event_id)
```

does not represent the domain correctly.

The intended PostgreSQL strategy is a partial unique index conceptually equivalent to:

```sql
CREATE UNIQUE INDEX uq_active_registration
ON registrations (participant_id, event_id)
WHERE status IN ('PENDING', 'CONFIRMED');
```

The exact migration DDL will be reviewed during implementation.

### Reservation Expiration

`reservation_expires_at` is nullable.

For free Events, Registrations are immediately confirmed and do not require a temporary reservation expiration.

For paid Events, a `PENDING` Registration requires a reservation expiration timestamp.

Cross-table rules involving Event price will initially remain application-level business rules.

Expired `PENDING` registrations must eventually be transitioned to `CANCELLED` so they do not continue blocking future Registration attempts.

## payments

Initial fields:

```text
payment_id
registration_id
amount
status
created_at
updated_at
```

### Relationships

Each Registration can have at most one Payment in the initial MVP.

Therefore `registration_id` must be:

* a foreign key;
* unique.

### Payment Amount

Payment exists only for paid Events.

Therefore:

```text
amount > 0
```

The Payment stores its own amount rather than relying exclusively on `events.price`.

This preserves the historical financial value associated with the Payment.

## tickets

Initial fields:

```text
ticket_id
public_id
registration_id
status
issued_at
created_at
updated_at
```

### Relationships

A confirmed Registration can have at most one Ticket.

`registration_id` must therefore be unique.

Ticket ownership is derived from:

```text
Ticket
  ↓
Registration
  ↓
Participant
```

The Ticket does not need a duplicated `participant_id`.

### Public Identifier

`ticket_id` is an internal database identifier.

Tickets additionally receive a unique non-sequential public identifier.

UUID is the initial expected strategy.

This prevents the public API from relying on predictable sequential Ticket identifiers.

## Status Representation

Domain states will initially use textual database columns protected by `CHECK` constraints.

Initial allowed values:

### User

```text
PARTICIPANT
ORGANIZER
```

### Event

```text
DRAFT
PUBLISHED
CANCELLED
FINISHED
```

### Registration

```text
PENDING
CONFIRMED
CANCELLED
```

### Payment

```text
PENDING
APPROVED
FAILED
EXPIRED
CANCELLED
REFUNDED
```

### Ticket

```text
ACTIVE
CANCELLED
```

Java will represent these values using enums.

PostgreSQL ENUM types and dedicated lookup tables are not required initially.

## Audit Fields

Main domain tables contain:

```text
created_at
updated_at
```

Domain-specific timestamps are represented separately when necessary.

Examples:

```text
events.published_at
registrations.reservation_expires_at
tickets.issued_at
```

`updated_at` must not be reused as a substitute for domain-specific historical timestamps.

## Referential Integrity

Critical historical relationships should not use broad `ON DELETE CASCADE` behavior.

Deletion of one resource must not automatically destroy:

* Event history;
* Registration history;
* Payment history;
* Ticket history.

The default strategy for critical relationships should favor referential protection such as `RESTRICT` or `NO ACTION`.

## Initial Index Strategy

Indexes must correspond to real access patterns.

Initial justified queries include:

* User lookup by email;
* Organizer Event listing;
* Participant Registration listing;
* Event Registration listing;
* active Registration uniqueness;
* Payment lookup by Registration;
* Ticket lookup by Registration;
* Ticket lookup by public identifier.

Likely index candidates therefore include:

```text
users.email
events.organizer_id
registrations.participant_id
registrations.event_id
payments.registration_id
tickets.registration_id
tickets.public_id
```

Unique constraints already provide indexes for some of these access patterns.

Indexes should not be added simply because a column exists or is a foreign key.

## Concurrency

The database model alone does not guarantee Event capacity under concurrent Registration requests.

The initial implementation strategy will use transactional pessimistic locking on the relevant Event while:

1. checking committed capacity;
2. validating availability;
3. creating the Registration or reservation.

This prevents concurrent requests from independently observing the same final available spot and exceeding Event capacity.
