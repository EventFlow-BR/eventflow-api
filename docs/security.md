# EventFlow Security Strategy

## Overview

EventFlow uses authentication and authorization rules designed for a web application with a separate frontend and backend.

The initial security strategy uses:

* Spring Security;
* JWT;
* HttpOnly cookies;
* BCrypt;
* role-based authorization;
* resource ownership validation;
* explicit CORS and CSRF considerations.

Security decisions may evolve as new infrastructure and distributed components are introduced.

## Authentication

The initial authentication endpoints are expected to include:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

### Login Flow

The login flow is conceptually:

```text
credentials
    ↓
backend authentication
    ↓
JWT generation
    ↓
HttpOnly authentication cookie
    ↓
authenticated browser session
```

JWT represents the authentication token.

The HttpOnly cookie represents how the browser stores and transports that token.

The JWT must not be returned to the frontend for storage in `localStorage`.

## Password Storage

Passwords are never stored in plain text.

The initial password hashing strategy uses BCrypt.

The database stores only:

```text
password_hash
```

The following must never be exposed through API responses:

* raw passwords;
* password hashes.

## Authentication Cookie

Production authentication cookies are expected to use:

```text
HttpOnly = true
Secure = true
SameSite = Lax
Path = /
```

Development configuration may disable `Secure` when the application runs locally over HTTP.

The exact token expiration period will be defined during authentication implementation.

## Authorization

Authorization must consider more than the authenticated User role.

The general model is:

```text
authentication
+
role
+
resource ownership
+
business state
```

### Participant Authorization

Participants may access only their own private:

* Registrations;
* Tickets.

A Participant cannot access another Participant's Registration or Ticket.

### Organizer Authorization

Organizers may manage only their own Events.

Organizer access to Registration and occupancy information is limited to Registrations associated with Events owned by the authenticated Organizer.

Having the `ORGANIZER` role does not grant access to every Event in the system.

## Resource Ownership

Protected resources must be validated against the authenticated User.

Examples:

```text
Participant → Registration ownership
Participant → Ticket ownership through Registration

Organizer → Event ownership
Organizer → Registration access through Event ownership
```

Ownership checks are part of authorization and must not rely only on client-provided identifiers.

## HTTP Authorization Responses

The initial API policy is:

### 401 Unauthorized

Used when authentication is required but:

* authentication is missing;
* authentication is invalid;
* authentication has expired.

### 403 Forbidden

Used when an authenticated User is generally prohibited from performing an operation and revealing the authorization failure does not expose private resource existence.

### 404 Not Found

Used when:

* the requested resource does not exist;
* a private resource exists but belongs to another User.

Returning `404` for foreign private resources reduces information disclosure about resource existence.

## Public Access

Unauthenticated users may:

* list publicly visible Events;
* view publicly visible Event details.

Authentication is required for:

* Registration operations;
* Ticket access;
* Organizer Event management;
* private account information.

## CORS

During development, frontend and backend may run on different origins.

For example:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
```

Credentialed cross-origin requests therefore require explicit CORS configuration.

The backend should:

* allow only trusted frontend origins;
* allow credentials;
* avoid wildcard origins when credentials are enabled.

CORS is a browser cross-origin policy.

It is not a replacement for authentication or authorization.

## CSRF

HttpOnly cookies are automatically attached to eligible browser requests.

Because EventFlow uses cookie-based authentication, CSRF must be explicitly considered.

`HttpOnly` helps prevent JavaScript from directly reading the authentication cookie, but it does not by itself prevent Cross-Site Request Forgery.

CSRF protection must not be disabled automatically only because the backend exposes REST endpoints.

During implementation, the project will evaluate:

* `SameSite` behavior;
* trusted frontend origins;
* Spring Security CSRF protection;
* whether an explicit CSRF token is required.

Any decision to disable or customize CSRF protection must be justified.

## Logout

Logout removes the authentication cookie from the browser.

Because JWT is initially stateless, deleting the cookie does not cryptographically revoke a previously copied token.

The initial MVP will rely on:

* limited JWT expiration;
* cookie removal on logout.

The following mechanisms will not initially be introduced without a concrete requirement:

* JWT blacklist;
* refresh-token rotation;
* Redis-backed session storage.

## Sensitive Data

Sensitive authentication data must never be logged.

Application logs must not contain:

* raw passwords;
* password hashes;
* complete JWT values;
* authentication cookie contents.

Personal data should only be logged when necessary for legitimate operational purposes.

## DTO Boundaries and Mass Assignment

API requests must use dedicated DTOs rather than binding external input directly to JPA entities.

Clients must not be able to arbitrarily modify protected fields such as:

* database identifiers;
* password hashes;
* lifecycle statuses;
* audit timestamps;
* authorization-sensitive ownership fields.

Status changes must occur through explicit domain operations.

For example, a Participant must not be able to directly confirm their own Registration by submitting:

```json
{
  "status": "CONFIRMED"
}
```

## SQL Injection

Database access will use Spring Data JPA and parameterized persistence operations.

Dynamic query construction must avoid directly concatenating untrusted external input into SQL.

Using JPA does not remove the responsibility to review custom native queries or dynamically constructed queries.

## Authentication Data Exposure

Authentication responses must expose only the information required by the frontend.

A `/me` response may contain information such as:

```json
{
  "id": 10,
  "name": "Example User",
  "email": "user@example.com",
  "role": "PARTICIPANT"
}
```

It must not contain:

* password hashes;
* raw passwords;
* JWT values;
* internal authentication secrets.

## Initial Security Matrix

| Operation                        | Public | Participant | Organizer |
| -------------------------------- | :----: | :---------: | :-------: |
| Register account                 |   Yes  |      —      |     —     |
| Login                            |   Yes  |     Yes     |    Yes    |
| Logout                           |   No   |     Yes     |    Yes    |
| Get authenticated user           |   No   |     Yes     |    Yes    |
| Browse public Events             |   Yes  |     Yes     |    Yes    |
| View public Event                |   Yes  |     Yes     |    Yes    |
| Register for Event               |   No   |     Yes     |     No    |
| Cancel own Registration          |   No   |     Yes     |     No    |
| View own Registrations           |   No   |     Yes     |     No    |
| View own Tickets                 |   No   |     Yes     |     No    |
| Create Event                     |   No   |      No     |    Yes    |
| Edit own Event                   |   No   |      No     |    Yes    |
| Publish own Event                |   No   |      No     |    Yes    |
| Cancel own Event                 |   No   |      No     |    Yes    |
| View Registrations for own Event |   No   |      No     |    Yes    |
| View occupancy for own Event     |   No   |      No     |    Yes    |
| Modify another Organizer's Event |   No   |      No     |     No    |
| Directly approve Payment         |   No   |      No     |     No    |
| Directly create Ticket           |   No   |      No     |     No    |

Organizer operations additionally require resource ownership and valid domain state.

## Security Principles

EventFlow follows these initial security principles:

1. Never trust client-provided authorization-sensitive data.
2. Authenticate before accessing protected resources.
3. Validate role and resource ownership.
4. Keep lifecycle changes behind explicit domain operations.
5. Never expose password hashes or authentication tokens.
6. Use restrictive CORS configuration.
7. Treat CSRF explicitly because authentication uses cookies.
8. Avoid logging sensitive information.
9. Prefer least privilege.
10. Return private resources only to authorized owners.
11. Prefer `404 Not Found` for private resources owned by another User.
12. Review security decisions again when distributed components are introduced.
