# Accounts and access

Carlisle treats identity and authorization as backend concerns. Hiding a button in the web client is never considered an authorization boundary.

## Account model

Every account has a role and an operational status:

- `USER` — normal marketplace participant; can both request and perform tasks.
- `ADMIN` — administrative account used for moderation and dispute resolution.
- `ACTIVE` — account may authenticate and use protected endpoints.
- `SUSPENDED` — login is rejected and existing bearer tokens no longer authenticate.

Public registration always creates `USER / ACTIVE`. A request payload cannot select an administrative role.

Authentication methods are kept separate from the core user record. `user_auth_identities` links a doFast user to a stable provider subject and currently reserves providers `GOOGLE` and `APPLE`. A user may have at most one identity from each provider, while a provider subject may belong to only one doFast account.

`users.password_login_enabled` explicitly distinguishes accounts that can authenticate with a local password from accounts created only through a federated identity. Federated-only accounts still contain an unguessable password hash to satisfy the legacy non-null schema, but password authentication is disabled and does not depend on that placeholder.

## Sign in with Google

The web client uses Google Identity Services only to obtain a Google ID token. It sends that credential to `POST /users/login/google`; the browser never decides whether a Google identity is trusted.

The API verifies the ID token with Google's Java verifier and the configured OAuth Web Client ID. Verification covers Google's signature plus the token audience, issuer and expiry. The backend additionally requires a verified email claim.

Google's stable `sub` claim is stored as `provider_subject` and is the primary external identity key. Email is metadata and is not used as the durable Google identifier because a user's email can change.

Account handling rules:

1. a previously linked Google `sub` signs in to its existing doFast user even if the Google email later changes;
2. a new Google identity with no matching email creates a normal `USER / ACTIVE` doFast account and its wallet;
3. an existing local account may be auto-linked by email only when Google is authoritative for that address (Gmail, or a verified hosted Google Workspace domain);
4. a third-party email that merely belongs to a Google Account is not silently linked to an existing local account — the user must first prove control of the existing doFast account in a future explicit account-link flow;
5. suspended users remain suspended regardless of which login method they use.

Configuration uses the same Google OAuth Web Client ID on both sides:

```text
GOOGLE_AUTH_CLIENT_ID=<web OAuth client id used by the API verifier>
VITE_GOOGLE_AUTH_CLIENT_ID=<same public web OAuth client id compiled into the web client>
```

The OAuth client ID is not a secret, but Google Cloud Console must restrict its authorized JavaScript origins to the real doFast origins. Production must use HTTPS.

The provider model intentionally includes `APPLE` already so Sign in with Apple can be added without redesigning the account schema.

## Administrator bootstrap

There are no hard-coded administrator credentials in the repository. A dedicated initial administrator can be created at application startup by setting both:

- `ADMIN_BOOTSTRAP_EMAIL`
- `ADMIN_BOOTSTRAP_PASSWORD` (minimum 12 characters)

`ADMIN_BOOTSTRAP_NICKNAME` is optional. The bootstrap only creates the account when the email is unused. If that email already belongs to a normal user, startup fails rather than silently promoting an unverified public registration.

For production, bootstrap values are deployment secrets and must not be committed. After the first administrator exists, future administrator lifecycle should move to an audited administrative workflow.

## Authentication

The API remains stateless and uses signed bearer JWTs. Local-password login and successful provider login both converge on the same doFast access-token response, so downstream authorization, WebSocket access and domain services do not depend on the upstream identity provider. Invalid credentials return `401`; suspended accounts return `403`.

The web client currently stores the access token in `sessionStorage`, so it survives a page refresh but is cleared when the browser session ends. Before handling real customer money, the authentication roadmap includes short-lived access tokens plus a hardened HttpOnly refresh-session flow and the accompanying CSRF model.

## Authorization

The JWT filter reloads the account on every authenticated request. This intentionally means role/status changes take effect immediately even when an older token still exists.

Spring Security grants `ROLE_USER` or `ROLE_ADMIN`; `/admin/**` requires `ROLE_ADMIN`. Public endpoints are intentionally limited to registration/login (including provider login), marketplace discovery, public profile summaries, health and the Stripe webhook endpoint.

## Current administrative surface

`/admin/overview` exposes account counts and `/admin/users` exposes account management to administrators only. Normal users can never access these endpoints. Administrators may suspend/reactivate normal users; the current endpoint intentionally refuses to suspend other administrators.