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
8. only the resulting stable Apple `sub` is treated as the external identity, then doFast issues its normal bearer JWT.

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
VITE_APPLE_AUTH_REDIRECT_URI=<same registered return URL>
```

The private `.p8` key is a deployment secret and must never be committed or exposed as a Vite variable.

## Administrator bootstrap

There are no hard-coded administrator credentials in the repository. A dedicated initial administrator can be created at application startup by setting both:

- `ADMIN_BOOTSTRAP_EMAIL`
- `ADMIN_BOOTSTRAP_PASSWORD` (minimum 12 characters)

`ADMIN_BOOTSTRAP_NICKNAME` is optional. The bootstrap only creates the account when the email is unused. If that email already belongs to a normal user, startup fails rather than silently promoting an unverified public registration.

For production, bootstrap values are deployment secrets and must not be committed. After the first administrator exists, future administrator lifecycle should move to an audited administrative workflow.

## Authentication

The API remains stateless and uses signed bearer JWTs. Local-password login and successful Google/Apple authentication converge on the same doFast access-token response, so downstream authorization, WebSocket access and domain services do not depend on the upstream identity provider. Invalid credentials return `401`; suspended accounts return `403`.

The web client currently stores the access token in `sessionStorage`, so it survives a page refresh but is cleared when the browser session ends. Before handling real customer money, the authentication roadmap includes short-lived access tokens plus a hardened HttpOnly refresh-session flow and the accompanying CSRF model.

## Authorization

The JWT filter reloads the account on every authenticated request. This intentionally means role/status changes take effect immediately even when an older token still exists.

Spring Security grants `ROLE_USER` or `ROLE_ADMIN`; `/admin/**` requires `ROLE_ADMIN`. Public endpoints are intentionally limited to registration/login (including provider login/challenges), marketplace discovery, public profile summaries, health and the Stripe webhook endpoint.

## Current administrative surface

`/admin/overview` exposes account counts and `/admin/users` exposes account management to administrators only. Direct suspension through the generic status endpoint is intentionally forbidden: account suspension must come from the audited enforcement flow for a reviewed report and must pass active-lifecycle safety checks. The generic status endpoint is a narrow recovery path that only reactivates an already suspended account.

Every successful recovery writes an immutable `admin_user_reactivation_audits` row containing the target account, acting administrator, `SUSPENDED -> ACTIVE` transition and timestamp. Administrators can inspect that history through `GET /admin/users/{id}/reactivation-audits`; the endpoint is read-only, validates that the target account exists and returns the newest audit first. No location, escrow or job-private data is included in the response.
