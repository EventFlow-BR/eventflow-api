# EventFlow Business Rules

This document describes the initial business rules defined for the EventFlow MVP.

These rules represent the current understanding of the domain and may evolve when new requirements or implementation constraints are discovered.

## Actors

The MVP initially supports two application roles:

* `PARTICIPANT`
* `ORGANIZER`

Each account has a single application role in the initial MVP.

## Public Access

Users do not need to authenticate to:

* browse published events;
* view publicly available event details.

Authentication is required for operations involving:

* registrations;
* tickets;
* private user data;
* organizer event management.

## Participant

A Participant may:

* register for eligible events;
* cancel their own registration before the event starts;
* list their own registrations;
* view their own registrations;
* list their own tickets;
* view their own tickets.

A Participant cannot:

* create or manage events;
* access registrations belonging to other participants;
* access tickets belonging to other participants;
* directly modify Payment status;
* directly create Tickets;
* directly modify protected domain status fields.

## Organizer

An Organizer may:

* create events;
* list their own events;
* view their own events;
* edit their own events when allowed;
* publish their own events;
* cancel their own events;
* view registrations associated with their own events;
* monitor occupancy of their own events.

An Organizer cannot manage Events belonging to another Organizer.

Authorization for organizer operations requires both:

* the `ORGANIZER` role;
* ownership of the requested Event.

## Event Lifecycle

The initial Event states are:

* `DRAFT`
* `PUBLISHED`
* `CANCELLED`
* `FINISHED`

Valid transitions:

```text
DRAFT ──publish──► PUBLISHED ──time──► FINISHED
  │                    │
cancel               cancel
  │                    │
  └──────► CANCELLED ◄──┘
```

`CANCELLED` and `FINISHED` are terminal states.

The following transitions are not allowed:

* `PUBLISHED → DRAFT`
* `CANCELLED → DRAFT`
* `CANCELLED → PUBLISHED`
* `FINISHED → DRAFT`
* `FINISHED → PUBLISHED`

## Event Creation

Every Event is created as `DRAFT`.

A persisted Event must already contain the required information.

`DRAFT` means that the Event is valid but has not yet been published.

Initial Event rules include:

* capacity must be greater than zero;
* price must be greater than or equal to zero;
* start date must be before end date;
* start date must be in the future when the Event is created;
* the Event must belong to an Organizer.

A price of zero represents a free Event.

## Event Editing

### DRAFT

An Organizer may change:

* name;
* description;
* location;
* start date;
* end date;
* capacity;
* price.

### PUBLISHED

An Organizer may change:

* name;
* description;
* location;
* capacity.

After publication, the following fields become immutable in the MVP:

* start date;
* end date;
* price.

Capacity may never be reduced below the number of committed spots.

### CANCELLED and FINISHED

Business-relevant Event changes are not allowed.

## Event Visibility

Published Events remain publicly accessible after becoming:

* `CANCELLED`;
* `FINISHED`.

This preserves historical information for participants.

An Event cancelled while still in `DRAFT` remains private because it was never publicly published.

The system must preserve whether an Event has ever been published.

## Registration Lifecycle

The initial Registration states are:

* `PENDING`
* `CONFIRMED`
* `CANCELLED`

`CANCELLED` is terminal.

A cancelled Registration is not reactivated.

If a participant becomes eligible again, a new Registration must be created.

## Free Event Registration

For a free Event:

```text
Participant registers
        ↓
Registration CONFIRMED
        ↓
Ticket issued
```

The confirmed Registration immediately consumes Event capacity.

## Paid Event Registration

For a paid Event:

```text
Participant registers
        ↓
Registration PENDING
        ↓
Payment PENDING
        ↓
temporary capacity reservation
```

The initial reservation timeout is 15 minutes.

A valid `PENDING` Registration consumes one Event spot during the reservation period.

## Committed Capacity

Committed spots are:

```text
CONFIRMED registrations
+
non-expired PENDING registrations
```

Available capacity is:

```text
event capacity - committed spots
```

Expired and cancelled registrations do not consume capacity.

## Registration Eligibility

A Participant may create a new Registration only when:

* the Event is `PUBLISHED`;
* the Event has not started;
* available capacity exists;
* the Participant does not already have an active Registration for that Event.

Active registrations are:

* valid `PENDING`;
* `CONFIRMED`.

A previous `CANCELLED` Registration does not prevent a future Registration attempt.

## Reservation Expiration

When a paid Registration reservation reaches its expiration time without valid payment approval:

```text
Payment → EXPIRED
Registration → CANCELLED
Capacity → released
```

Expiration is strict.

Payment approval processed after the reservation expiration time cannot confirm the expired Registration.

If money was captured after expiration, the system must execute a compensation flow that results in refund.

## Registration Cancellation

Participants may voluntarily cancel `PENDING` or `CONFIRMED` registrations before the Event starts.

Cancellation releases capacity.

For paid confirmed registrations, voluntary cancellation before the Event starts generates a full refund.

## Event Cancellation

When an Organizer cancels an Event:

* active `PENDING` registrations become `CANCELLED`;
* active `CONFIRMED` registrations become `CANCELLED`;
* pending payments are cancelled;
* approved payments require full refund;
* active tickets become `CANCELLED`.

The Event remains publicly visible if it had previously been published.

## Finished Events

When an Event becomes `FINISHED`, confirmed Registrations remain `CONFIRMED`.

This preserves the historical outcome of the registration.

## Payment Lifecycle

The initial Payment states are:

* `PENDING`
* `APPROVED`
* `FAILED`
* `EXPIRED`
* `CANCELLED`
* `REFUNDED`

Normal transitions include:

```text
PENDING → APPROVED
PENDING → FAILED
PENDING → EXPIRED
PENDING → CANCELLED
APPROVED → REFUNDED
```

Payment exists only for paid Events.

Payment processing will initially be simulated internally.

No real payment gateway belongs to the initial MVP.

## Refund Policy

The MVP uses full automatic refunds.

A Payment must be refunded when:

* a participant cancels a confirmed paid Registration before the Event starts;
* an Organizer cancels the Event after payment approval;
* a Payment is approved after its reservation has already expired.

The MVP does not initially support:

* partial refunds;
* cancellation fees;
* non-refundable tickets;
* configurable refund deadlines.

## Ticket Lifecycle

The initial Ticket states are:

* `ACTIVE`
* `CANCELLED`

A Ticket is created only after a Registration becomes `CONFIRMED`.

Each confirmed Registration has exactly one Ticket.

Participants cannot create Tickets directly.

When a confirmed Registration is cancelled:

```text
Ticket ACTIVE → CANCELLED
```

When an Event finishes normally, the Ticket remains `ACTIVE` as historical evidence that a valid Ticket was issued.

Check-in and ticket usage tracking are outside the initial MVP.

## Out of Scope for the Initial MVP

The following features are intentionally excluded from the initial MVP:

* social login;
* password recovery;
* email verification;
* event recommendations;
* ratings and reviews;
* favorites;
* advanced search;
* multiple ticket types;
* ticket batches;
* coupons;
* promotional codes;
* waiting lists;
* seat selection;
* ticket transfers;
* QR Code check-in;
* attendance tracking;
* real payment gateway integration;
* organizer organizations and teams;
* admin dashboard;
* advanced analytics and reports.
