# EventFlow v1 security

The backend uses Spring Security, BCrypt, signed JWTs in an HttpOnly cookie, explicit CSRF protection, role checks, and ownership checks. It has no frontend, Bearer-header authentication, refresh-token flow, or Redis session store.

Sources: [SecurityConfig](../src/main/java/br/com/eventflow/auth/SecurityConfig.java), [AuthController](../src/main/java/br/com/eventflow/auth/AuthController.java), [JwtAuthenticationFilter](../src/main/java/br/com/eventflow/auth/JwtAuthenticationFilter.java), and [JwtService](../src/main/java/br/com/eventflow/auth/JwtService.java).

## Authentication

`POST /api/auth/register` validates a name, email, password, and one role: PARTICIPANT or ORGANIZER. Organizer registration is open; there is no administrative approval mechanism. Email is normalized and protected by a database unique constraint. Passwords are stored as BCrypt hashes.

`POST /api/auth/login` verifies credentials and returns user information while setting the JWT cookie. The JWT is not returned in the JSON body. Its signed claims include the user ID as subject, role, issue time, and expiration. Signature and expiration are validated by the cookie authentication filter; malformed/expired tokens do not establish authentication.

The JWT secret is interpreted as UTF-8 HMAC key bytes. Outside the local profile, `JWT_SECRET` is required. Supply an unpredictable private key of at least 32 bytes; never use the bundled local fallback in production. Default `JWT_EXPIRATION` is 3600000 milliseconds, or one hour. There is no custom issuer/audience configuration in this implementation.

`GET /api/auth/me` resolves the authenticated user's current database information. JWT authentication is stateless: the filter establishes a principal from signed claims, not from a server-side login session. It does not reload the user's database record on every protected request.

## Cookies

| Cookie | Purpose | Attributes |
|---|---|---|
| `eventflow_token` | JWT authentication; name configurable via `app.auth.cookie-name` | HttpOnly, SameSite=Lax, Path=/, Secure configurable, max-age based on JWT expiration |
| `XSRF-TOKEN` | Native cookie-backed CSRF repository | HttpOnly, SameSite=Lax, Path=/, same Secure setting; no explicit persistent max-age configured |

`AUTH_COOKIE_SECURE` defaults to true and defaults to false under `local`, enabling host-local HTTP development. Retain Secure and use HTTPS for deployment. HttpOnly prevents direct JavaScript cookie access; it does not replace CSRF protection or defend against every consequence of XSS.

## CSRF contract

```text
register/login (public, CSRF-exempt)
    --> JWT HttpOnly cookie
    --> authenticated GET /api/auth/csrf
    --> JSON {headerName, token} + XSRF-TOKEN cookie
    --> mutating request with both cookies and header X-XSRF-TOKEN
    --> native Spring Security CSRF validation
```

`CookieCsrfTokenRepository` stores the CSRF token in its separate cookie. The endpoint materializes the token and returns the native request token; Spring Security's default token handling remains in place. Send the JSON `token` exactly as returned, rather than substituting the cookie's raw contents. No JavaScript access to JWT or CSRF cookies is needed.

POST/PUT operations and logout require the CSRF cookie plus a valid header. Missing token/header, invalid token, or missing CSRF cookie is rejected with 403. A valid CSRF token does not grant a role or bypass ownership/business-state validation.

The CSRF endpoint requires JWT authentication. It does not create an HTTP session/JSESSIONID for token storage. Login/register remain explicitly exempt; CSRF has not been disabled globally.

Logout is `permitAll` at the authorization layer, but remains protected by the CSRF filter. The ordinary client obtains CSRF while authenticated before calling it. The controller clears the JWT cookie; it does not revoke an already-copied JWT, remove its signature validity, or implement refresh-token/blacklist storage.

See [AuthCsrfIntegrationTest](../src/test/java/br/com/eventflow/auth/AuthCsrfIntegrationTest.java): it performs register/login, obtains the token over HTTP, and successfully creates/updates an event without `.with(csrf())`. It also checks missing/invalid token handling, logout, HttpOnly/Secure cookies, and unauthenticated token acquisition.

## Authorization and visibility

| Operation | Current rule |
|---|---|
| Register/login | Public and CSRF-exempt |
| Current user / CSRF token | Authenticated |
| Logout | CSRF-protected; permitAll authorization |
| Event creation | ORGANIZER |
| Event edit/publish/cancel | ORGANIZER and event owner, plus business eligibility |
| Event list | Authenticated; participants see PUBLISHED, organizers see their own events |
| Event by ID | PUBLISHED visible to either authenticated role; otherwise organizer owner only |
| Reservation | PARTICIPANT, plus event/capacity/duplicate rules |
| Payment / individual cancellation | PARTICIPANT and registration owner, plus business eligibility |
| Health | Public, overall status only |
| Prometheus | Exposed in local only by default; any authenticated user when enabled |

Actor IDs come from the principal, not an ownership ID in request payloads. Request DTOs do not expose arbitrary status, password-hash, owner, or audit-field assignment. Database queries use JPA binding or parameterized native SQL.

Owner-checked private operations hide another user's resource with 404. Business-state conflicts return 409, and DTO validation errors return 400. AuthService returns 401 for invalid login credentials or an invalid current-user lookup. The security chain does not configure a custom authentication entry point/JSON error contract; do not assume every missing/invalid JWT is returned as a standardized JSON 401. Role and CSRF failures are handled by Spring Security.

Event visibility is checked in EventService outside EventCachedReadService, including on cache hits. The cache holds shared event data, not a user's successful authorization decision. Because cached status/owner attributes are used, this is not a guarantee of immediate visibility changes under stale cache data.

## Local HTTP walkthrough

The application must be running with the local profile as described in the [README](../README.md#running-locally). These optional Bash examples require `curl` and `jq` for JSON token extraction; neither is an application runtime dependency. Use a fresh local account email if the example has already been registered.

```bash
eventflow_base=http://localhost:8080
eventflow_cookie_jar=$(mktemp)

curl -fsS -X POST "$eventflow_base/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Organizer","email":"demo-organizer@example.com","password":"local-demo-password","role":"ORGANIZER"}'

curl -fsS -X POST "$eventflow_base/api/auth/login" \
  -H 'Content-Type: application/json' \
  -c "$eventflow_cookie_jar" \
  -d '{"email":"demo-organizer@example.com","password":"local-demo-password"}'

curl -fsS "$eventflow_base/api/auth/me" -b "$eventflow_cookie_jar"

eventflow_csrf=$(curl -fsS "$eventflow_base/api/auth/csrf" \
  -b "$eventflow_cookie_jar" -c "$eventflow_cookie_jar" | jq -er '.token')
```

The CSRF response has this shape (the token is opaque and generated per flow):

```json
{"headerName":"X-XSRF-TOKEN","token":"<token returned by Spring Security>"}
```

A protected request without its header is rejected:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  "$eventflow_base/api/auth/logout" -b "$eventflow_cookie_jar"
# 403
```

Send the obtained token and preserve both cookies for a valid logout:

```bash
curl -i -X POST "$eventflow_base/api/auth/logout" \
  -b "$eventflow_cookie_jar" -c "$eventflow_cookie_jar" \
  -H "X-XSRF-TOKEN: $eventflow_csrf"
# 204 No Content; eventflow_token is cleared

rm "$eventflow_cookie_jar"
unset eventflow_csrf
```

Use the same cookie/header combination for event, reservation, payment, and cancellation writes. Obtain a token for each independently authenticated cookie jar/account. Do not commit or share cookie jars or real credentials.

## Operational exposure and limits

- Only health is exposed by Actuator in the default configuration; local also exposes Prometheus. Health omits component details publicly.
- Prometheus has no dedicated operations role when enabled. It is not exposed in the production-like default configuration; explicitly re-enabling it requires considering deployment network/access policy.
- No explicit CORS configuration is implemented. The current backend-only contract does not provision a cross-origin browser frontend or credentialed CORS allowlist.
- GlobalExceptionHandler sanitizes unexpected application errors; the API does not intentionally return password hashes, JWT values in JSON, or stack traces.
- The local Compose/profile contains known development credentials and a local JWT fallback; it is not a production security configuration.
- HTTPS termination, production secrets, infrastructure exposure, and production smoke testing remain deployment work. This document describes implemented controls, not a completed production-security assessment.
