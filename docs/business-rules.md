# EventFlow v1 business rules

These rules describe the implemented backend services and HTTP contract. [Architecture](architecture.md) covers locks and messaging; [security](security.md) covers cookies and CSRF. There is no frontend in this version.

## Actors and access

Each account has one role, chosen during registration: `ORGANIZER` or `PARTICIPANT`. There is no administrator/role-approval workflow.

- Organizers create and manage their own events.
- Participants reserve places, pay for their own registrations, and cancel them when eligible.
- Event queries require authentication. Organizers do not gain access to private events owned by another organizer.
- Operation actor IDs come from the authenticated principal, not a client-supplied ownership field.
- Private resources belonging to another owner return 404 in the owner-checked operations.
- No endpoints list a participant's registrations, event participants, occupancy, or tickets.

## Event creation and editing

An event contains an organizer, name, description, location, start/end timestamps, capacity, price, and status. Creation sets `DRAFT`.

The HTTP DTOs require nonblank text, required timestamps, `capacity > 0`, `price > 0`, and monetary precision of at most eight integer and two fractional digits. The service requires `startDate < endDate`. Draft creation/editing does not separately require a future start date; publication does.

Only the owner with ORGANIZER access can edit. Editing is a full PUT of the editable details and is permitted only for DRAFT. PUBLISHED, CANCELLED, and FINISHED editing is rejected with 409. Consequently, price, capacity, and dates cannot be changed after reservations become possible.

## Event lifecycle and visibility

```text
DRAFT --publish, future start--> PUBLISHED
  |                                |
  `--cancel if eligible------------+--> CANCELLED

FINISHED: reserved state; no automatic or public transition in v1
```

Publication requires DRAFT and `startDate > now`; it sets `published_at`. Republishing or publishing an already-started event returns 409.

Current query rules are deliberately narrower than historical-public-access plans:

| Query | Behavior |
|---|---|
| Participant list | PUBLISHED events |
| Organizer list | That organizer's events, across states |
| Event by ID, PUBLISHED | Available to either authenticated role |
| Event by ID, any other state | Available only to its organizer owner; others receive 404 |

`published_at` is retained after cancellation, but is not used as a historical-public-visibility flag. Events do not automatically become FINISHED when time passes. An old PUBLISHED event may still be returned by reads, while time checks prevent new reservation/payment.

## Event cancellation

The owner can cancel a DRAFT or PUBLISHED event only if:

1. The event has not started: `now < startDate`.
2. There are zero active registrations.

Active registrations use the capacity query's definition: CONFIRMED, or PENDING with `reservationExpiresAt > now`. A PENDING reservation at/past its deadline does not block cancellation while waiting for the scheduler.

CANCELLED/FINISHED events, started events, and events with active registrations are rejected with 409. Other-owner requests retain 404 behavior.

The operation changes only the Event. There is no cascade, registration cancellation, refund in bulk, or additional event-cancellation message. Historical inactive registrations remain unchanged.

## Reservation and capacity

A PARTICIPANT can reserve only when:

- The Event is PUBLISHED and has not started.
- The participant exists with the participant role.
- That participant has no active registration for the event.
- Occupied capacity is below event capacity.

```text
occupied capacity = CONFIRMED registrations
                  + PENDING registrations with reservationExpiresAt > now
```

Every reservation starts as PENDING with deadline `now + reservation timeout`; the default timeout is 15 minutes. No Payment is created during reservation. There is no free-event confirmation branch.

The Event pessimistic lock protects duplicate/capacity checks and insert. The application does not reactivate old registrations: cancellation or logical expiration permits a new reservation, subject to normal event/capacity rules.

A reservation at/past its deadline no longer occupies capacity even if its persisted status is still PENDING. This distinction is also used by duplicate detection and event cancellation.

## Payment simulation

Only the registration owner with PARTICIPANT access can invoke payment. Payment first locks the Event, then the Registration, and obtains the validation time after those locks.

Eligibility requires:

```text
Registration == PENDING
AND reservationExpiresAt > now
AND Event == PUBLISHED
AND Event.startDate > now
AND no existing Payment for this registration
```

Invalid state, expiration, event unavailability, or duplicate payment is rejected with 409. Another owner's registration is hidden with 404. Rejection creates no Payment, does not confirm the Registration, and publishes no confirmation event.

On success, a Payment is created directly as APPROVED using the event price, and the registration becomes CONFIRMED in the same JPA transaction. The Payment stores its own amount. The event cannot be edited after publication, so the supported API cannot change that price after reservation.

A valid payment holding the Event lock can commit after the reservation's timestamp passes: another reservation waits for that lock and then sees the confirmed place. A payment that acquires its locks only after expiration fails eligibility. This is not a real gateway, external capture, or financial settlement.

## Individual cancellation and refund

The participant owner may cancel a registration before the event starts if its state is:

- PENDING with an unexpired reservation; or
- CONFIRMED with an APPROVED simulated Payment.

The operation locks the Registration. A valid pending cancellation sets CANCELLED without refund. A confirmed cancellation sets its Payment to REFUNDED and its Registration to CANCELLED in one transaction. Missing/non-approved payment for a confirmed registration causes conflict rather than silently proceeding.

Expired, already-cancelled, or otherwise ineligible registrations are rejected with 409. Cancellation frees capacity on transaction completion. Refund is a state change for the entire stored simulated payment, not an external money transfer. There are no partial refunds, fees, or separate refund endpoints.

## Expiration

The scheduler uses a fixed delay of 60 seconds by default. It locks PENDING registrations whose deadlines satisfy `reservationExpiresAt <= now`, changes them to EXPIRED, and emits expiration application events in that transaction.

Expiration does not create or expire a Payment. Payment does not exist until simulated approval. Expired registrations are historical records, not CANCELLED registrations and not reactivated reservations.

## Implemented states

| Aggregate | States used by current flows | Reserved values without a current flow |
|---|---|---|
| Event | DRAFT, PUBLISHED, CANCELLED | FINISHED |
| Registration | PENDING, CONFIRMED, CANCELLED, EXPIRED | None |
| Payment | APPROVED, REFUNDED | PENDING, FAILED, EXPIRED, CANCELLED |

Normal registration transitions are PENDING → CONFIRMED, PENDING → EXPIRED, PENDING → CANCELLED, and CONFIRMED → CANCELLED. A new eligible reservation creates a new record.

## Transactional side effects

- Reservation creation emits a local event used for the reserved counter.
- Confirmation, individual cancellation, and expiration emit application events used for RabbitMQ publication and business counters.
- RabbitMQ publication and counter increments use AFTER_COMMIT listeners; rollback does not trigger those side effects in the covered tests.
- Consumer processing currently logs the registration events; it does not produce a Ticket or email.
- Post-commit publication is not durable through an Outbox; its delivery limitations are documented in [architecture](architecture.md#messaging-and-delivery-boundaries).

## Deliberately outside v1

Free events, tickets/check-in, notifications/email, real payment processing, event-cancellation cascade, bulk refunds, and additional registration/participant/occupancy queries are not implemented features. Future extensions are optional and do not change this contract.

## Implementation references

- [EventService](../src/main/java/br/com/eventflow/event/EventService.java)
- [Event DTOs](../src/main/java/br/com/eventflow/event/dto)
- [RegistrationService](../src/main/java/br/com/eventflow/registration/RegistrationService.java)
- [PaymentService](../src/main/java/br/com/eventflow/payment/PaymentService.java)
- [RegistrationCancellationService](../src/main/java/br/com/eventflow/registration/RegistrationCancellationService.java)
- [RegistrationExpirationService](../src/main/java/br/com/eventflow/registration/RegistrationExpirationService.java)
