# Accounts and access

Carlisle treats identity and authorization as backend concerns. Hiding a button in the web client is never considered an authorization boundary.

## Account model

Every account has a role and an operational status:

- `USER` — normal marketplace participant; can both request and perform tasks.
- `ADMIN` — administrative account used for moderation and dispute resolution.
- `ACTIVE` — account may authenticate and use protected endpoints.
- `SUSPENDED` — login is rejected and existing bearer tokens no longer authenticate.

Public registration always creates `USER / ACTIVE`. A request payload cannot select an administrative role.

Authentication methods are kept separate from the core user record. `user_auth_identities` links a doFast user to a stable provider subject for `GOOGLE` or `APPLE`. A user may have at most one identity from each provider, while a provider subject may belong to only one doFast account.

`users.password_login_enabled` explicitly distinguishes accounts that can authenticate with a local password from accounts created only through a federated identity. Federated-only accounts still contain an unguessable password hash to satisfy the legacy non-null schema, but password authentication is disabled and does not depend on that placeholder.

`users.auth_version` is a monotonic credential version. Signed access JWTs carry the current value in the `av` claim and the authentication filter compares it with the current database value on every Bearer request. Password change and password reset increment it, which immediately invalidates access JWTs issued before the credential change.

## Sign in with Google

The web client uses Google Identity Services only to obtain a Google ID token. It sends that credential to `POST /users/login/google`; the browser never decides whether a Google identity is trusted.

The API verifies the ID token with Google's Java verifier and the configured OAuth Web Client ID. Verification covers Google's signature plus the token audience, issuer and expiry. The backend additionally requires a verified email claim.

Google's stable `sub` claim is stored as `provider_subject` and is the primary external identity key. Email is metadata and is not used as the durable Google identifier because a user's email can change.

Account handling rules:

1. a previously linked Google `sub` signs in to its existing doFast user even if the Google email later changes;
2. a new Google identity with no matching email creates a normal `USER / ACTIVE` doFast account and its wallet;
3. an existing local account may be auto-linked by email only when Google is authoritative for that address (Gmail, or a verified hosted Google Workspace domain);
4. a third-party email that merely belongs to a Google Account is not silently linked to an existing local account;
5. suspended users remain suspended regardless of which login method they use.

Configuration uses the same Google OAuth Web Client ID on both sides:

```text
GOOGLE_AUTH_CLIENT_ID=<web OAuth client id used by the API verifier>
VITE_GOOGLE_AUTH_CLIENT_ID=<same public web OAuth client id compiled into the web client>
```

The OAuth client ID is not a secret, but Google Cloud Console must restrict its authorized JavaScript origins to the real doFast origins. Production must use HTTPS.

## Sign in with Apple

The Apple flow uses the authorization-code flow with a server-generated one-time login challenge. It deliberately does more than decode the `id_token` returned to browser JavaScript.

1. the browser requests `POST /users/login/apple/challenge`;
2. the API creates cryptographically random `state` and `nonce`, stores only their SHA-256 hashes and returns the raw challenge values to that browser;
3. Apple JS is initialized with the configured Services ID, redirect URI, state and nonce;
4. the browser returns Apple's authorization `code`, Apple's returned `state`, the challenge id and original nonce to `POST /users/login/apple`;
5. the API locks and consumes the challenge exactly once, rejecting an expired, reused or mismatched state/nonce;
6. the API generates a short-lived ES256 Apple client secret from the server-only `.p8` private key and exchanges the authorization code at Apple's token endpoint;
7. the API verifies the `id_token` returned directly by Apple's token endpoint against Apple's JWK set, expected issuer, audience, expiry and the exact nonce;
8. only the resulting stable Apple `sub` is treated as the external identity, then doFast issues its normal browser session.

The API stores no Apple access token or refresh token because the current product only needs authentication. The authorization code is single-use and the doFast login challenge is also single-use.

Apple can provide a private relay email and may provide the user's name only on the first authorization. Returning users are therefore resolved by Apple `sub`, not by email, and can sign in even when a later Apple token omits email/name. A brand-new Apple identity must provide a verified email so doFast can create its account record.

Apple identities are never silently linked to an existing doFast account merely because the email matches. That case returns a conflict and is reserved for a future explicit account-linking flow where the user first proves control of the existing doFast account. This also avoids accidental duplication/linking issues caused by Hide My Email.

Server-only configuration:

```text
APPLE_AUTH_CLIENT_ID=<Apple Services ID>
APPLE_AUTH_REDIRECT_URI=<registered HTTPS return URL>
APPLE_AUTH_TEAM_ID=<Apple Developer Team ID>
APPLE_AUTH_KEY_ID=<Sign in with Apple key id>
APPLE_AUTH_PRIVATE_KEY_BASE64=<base64 of the downloaded .p8 file>
APPLE_AUTH_CHALLENGE_TTL_MINUTES=10
```

Browser build configuration:

```text
VITE_APPLE_AUTH_CLIENT_ID=<same Services ID>
VITE_APPLE_AUTH_REDIRECT_URI=<same registered HTTPS return URL>
```

The private `.p8` key is a deployment secret and must never be committed or exposed as a Vite variable.

## Administrator bootstrap

There are no hard-coded administrator credentials in the repository. A dedicated initial administrator can be created at application startup by setting both:

- `ADMIN_BOOTSTRAP_EMAIL`
- `ADMIN_BOOTSTRAP_PASSWORD` (minimum 12 characters)

`ADMIN_BOOTSTRAP_NICKNAME` is optional. The bootstrap only creates the account when the email is unused. If that email already belongs to a normal user, startup fails rather than silently promoting an unverified public registration.

For production, bootstrap values are deployment secrets and must not be committed. After the first administrator exists, future administrator lifecycle should move to an audited administrative workflow.

## Authentication and browser sessions

Password, Google and Apple authentication converge on the same doFast session boundary. The normal API remains stateless and authorizes requests with signed bearer JWT access tokens; the refresh cookie is **not** accepted as ambient authentication for ordinary domain endpoints.

Access tokens are deliberately short lived. The normal value is 10 minutes and the application rejects access-token TTL configuration above 15 minutes. The web client keeps the access token only in JavaScript module memory: it is not written to `localStorage` or `sessionStorage`. Reloading a page therefore discards the bearer and restores the browser session through the refresh flow instead of recovering a long-lived credential from Web Storage.

A successful password, Google or Apple login also creates a durable `auth_refresh_sessions` row and returns two cookies:

- `dofast_refresh` — a cryptographically random opaque refresh credential, `HttpOnly`, scoped to `/`;
- `dofast_csrf` — a separate random CSRF value readable by the browser client.

Only SHA-256 hashes of both values are stored in PostgreSQL. Raw refresh/CSRF secrets exist only in the browser cookies and in the request that rotates them.

`POST /users/session/refresh` requires all three values to agree: the HttpOnly refresh cookie, the readable CSRF cookie and the `X-CSRF-Token` header. The cookie/header pair is compared in constant time and the resulting CSRF value must also match the hash bound to the locked refresh-session row. Ordinary Bearer-authenticated API calls do not become cookie authenticated, so this explicit CSRF boundary stays limited to refresh/logout operations.

Every successful refresh rotates both browser secrets. The old row is marked `ROTATED`, a successor is created in the same session family and a new short-lived access JWT is issued with the user's current `auth_version`. The frontend coalesces concurrent 401 responses in a tab into one refresh request and retries each original request once with the resulting Bearer.

Realtime uses the same in-memory token source. While a STOMP connection is active, the browser refreshes shortly before the access JWT expires, reconnects with the replacement Bearer and restores existing subscriptions on the new transport. Token rotation is observed in memory and does not introduce browser storage.

A short reuse grace window prevents a near-simultaneous duplicate refresh from destroying the valid successor family. A rotated token used after that grace is treated as replay: the active session family is revoked with `REUSE_DETECTED`. This contains a copied refresh credential without revoking unrelated sessions on the user's other devices.

Logout revokes the current refresh authority and clears both browser cookies. The controller clears cookies even when malformed/missing CSRF prevents a trustworthy server-side revoke, so the browser cannot become stuck with an inaccessible HttpOnly credential.

Changing the local password atomically updates the password hash, increments `auth_version` and revokes **all** active refresh sessions. The credential-version comparison means access JWTs issued before the change immediately stop authenticating as well; the system no longer waits for their normal short expiry.

Applying an account suspension increments `auth_version` and revokes every active refresh session in the same transaction. Suspended accounts also cannot log in or refresh. Separately, the JWT filter reloads the user on every Bearer-authenticated request and requires both `ACTIVE` status and an exact `auth_version` match, so the sanction takes effect immediately and pre-suspension credentials remain invalid after a later account reactivation.

The block-by-user-id mutation resolves its target through the same `ACTIVE` account boundary used by public profile and review reads. Missing and suspended targets both fail before the block relation is queried or a nickname can be disclosed. Existing private block history remains available to the blocker so established safety relationships can still be inspected and removed.

WebSocket/STOMP sessions follow the same credential lifecycle instead of becoming a separate long-lived authentication island. A successful STOMP `CONNECT` binds the WebSocket session id to the account email, exact `auth_version` and signed access-token expiry without retaining the raw JWT. Subsequent inbound frames revalidate that binding against the current database account and expiry. Immediately before each outbound broker `MESSAGE`, the API also requires the binding to remain unexpired and the account to remain `ACTIVE` with the same `auth_version`; expired, stale, suspended or unknown sessions are dropped before realtime chat, notifications or tracking data reaches the client. The underlying socket is not guaranteed to be physically closed at the instant credentials change, but it can no longer receive protected realtime messages and subsequent inbound frames are rejected. Session bindings are removed on disconnect and when expired or stale state is detected.

Default browser-session configuration:

```text
JWT_EXPIRATION_MS=600000
AUTH_REFRESH_TTL_DAYS=30
AUTH_REFRESH_REUSE_GRACE_SECONDS=15
AUTH_SESSION_RETENTION_DAYS=7
AUTH_SESSION_CLEANUP_INTERVAL_MS=3600000
AUTH_COOKIE_SAME_SITE=Strict
```

Local HTTP development uses `AUTH_COOKIE_SECURE=false`. The production Spring profile hard-wires `Secure=true`; the production Compose file cannot override that invariant. The deployed web and `/api` gateway are expected to be same-origin. A cross-site `VITE_API_BASE_URL` is not compatible with the default `SameSite=Strict` session policy and must not be introduced casually.

Memory-only access tokens reduce credential persistence and theft from browser storage, but they are not an XSS defense: malicious script executing in the live origin can still act with the current session. CSP, dependency hygiene, rate limiting and the remaining production security work stay relevant.

## Password recovery

`POST /users/password/forgot` is public and always returns the same `202 Accepted` empty response for a valid email payload. It does not disclose whether the email exists or whether the account supports local password authentication. Federated-only accounts are ignored internally and receive the same public behavior.

For eligible local-password accounts, the backend creates a random opaque one-time reset credential and persists only its SHA-256 hash in `auth_password_reset_tokens`. A new request invalidates earlier active reset links. The default TTL is 30 minutes and the configured range is limited to 5–60 minutes.

Outbound delivery is deliberately separated from the request transaction. The raw token is carried only in an in-memory application event. An asynchronous `AFTER_COMMIT` listener sends the SMTP email after the reset row is durable, so the public request is not blocked on SMTP latency and the raw token never needs to be stored in an outbox or database column. Delivery errors log only the internal user id.

`POST /users/password/reset` pessimistically locks the matching token hash. A successful reset updates the password hash, increments `auth_version`, marks the link used, invalidates other active reset links and revokes all active refresh sessions with `PASSWORD_RESET`. The used link cannot be replayed, and access JWTs issued before the reset immediately fail their credential-version check. Existing WebSocket sessions are credential-bound too: further inbound frames are rejected and protected outbound realtime messages are suppressed as soon as the database credential version no longer matches.

The web exposes `/forgot-password` and `/reset-password?token=...`. Local/CI delivery is disabled by default; production hard-wires SMTP delivery and requires an HTTPS reset URL and verified sender configuration. Full details and the runtime contract are documented in `docs/PASSWORD_RECOVERY.md`.

## Authorization

The JWT filter reloads the account on every authenticated request. Role/status and credential-version changes therefore take effect immediately even when a cryptographically valid older token still exists. WebSocket session revalidation applies the equivalent database-backed `ACTIVE`/`auth_version` boundary to established realtime sessions and to each protected outbound client message.

Spring Security grants `ROLE_USER` or `ROLE_ADMIN`; `/admin/**` requires `ROLE_ADMIN`. Public endpoints are intentionally limited to registration/login (including provider login/challenges), refresh/logout, forgot/reset password, marketplace discovery, public profile summaries, health and the Stripe webhook endpoint. Refresh/logout are public only at the Spring routing layer: their own opaque-cookie/CSRF validation is the authentication boundary. Reset-password endpoints are public by necessity and protect themselves with generic responses, opaque high-entropy one-time credentials and strict validation.

## Current administrative surface

`/admin/overview` exposes account counts and `/admin/users` exposes account management to administrators only. Direct suspension through the generic status endpoint is intentionally forbidden: account suspension must come from the audited enforcement flow for a reviewed report and must pass active-lifecycle safety checks. The generic status endpoint is a narrow recovery path that only reactivates an already suspended account.

Every successful recovery requires a non-blank administrator reason (maximum 1000 characters) and writes an immutable `admin_user_reactivation_audits` row containing the target account, acting administrator, `SUSPENDED -> ACTIVE` transition, normalized reason and timestamp. Migration V29 backfills legacy audit rows with an explicit marker that the reason was not captured previously and then enforces a non-blank database constraint. Administrators can inspect the complete history through `GET /admin/users/{id}/reactivation-audits`; the endpoint is read-only, validates that the target account exists and returns the newest audit first. No location, escrow or job-private data is included in the response. The admin web panel requires the reason before enabling recovery, loads history only on demand per account and refreshes it immediately after a successful reactivation.
