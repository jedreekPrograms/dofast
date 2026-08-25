# Accounts and access

Carlisle treats identity and authorization as backend concerns. Hiding a button in the web client is never considered an authorization boundary.

## Account model

Every account has a role and an operational status:

- `USER` — normal marketplace participant; can both request and perform tasks.
- `ADMIN` — administrative account used for moderation and, in later stages, dispute resolution.
- `ACTIVE` — account may authenticate and use protected endpoints.
- `SUSPENDED` — login is rejected and existing bearer tokens no longer authenticate.

Public registration always creates `USER / ACTIVE`. A request payload cannot select an administrative role.

## Administrator bootstrap

There are no hard-coded administrator credentials in the repository. A dedicated initial administrator can be created at application startup by setting both:

- `ADMIN_BOOTSTRAP_EMAIL`
- `ADMIN_BOOTSTRAP_PASSWORD` (minimum 12 characters)

`ADMIN_BOOTSTRAP_NICKNAME` is optional. The bootstrap only creates the account when the email is unused. If that email already belongs to a normal user, startup fails rather than silently promoting an unverified public registration.

For production, bootstrap values are deployment secrets and must not be committed. After the first administrator exists, future administrator lifecycle should move to an audited administrative workflow.

## Authentication

The API remains stateless and uses signed bearer JWTs. The login response contains the access token, its type, expiry duration and the current user snapshot. Invalid credentials return `401`; suspended accounts return `403`.

The web client currently stores the access token in `sessionStorage`, so it survives a page refresh but is cleared when the browser session ends. Before handling real customer money, the authentication roadmap includes short-lived access tokens plus a hardened HttpOnly refresh-session flow and the accompanying CSRF model.

## Authorization

The JWT filter reloads the account on every authenticated request. This intentionally means role/status changes take effect immediately even when an older token still exists.

Spring Security grants `ROLE_USER` or `ROLE_ADMIN`; `/admin/**` requires `ROLE_ADMIN`. Public endpoints are intentionally limited to registration/login, marketplace discovery, public profile summaries, health and the Stripe webhook endpoint.

## Current administrative surface

`/admin/overview` exposes account counts and `/admin/users` exposes account management to administrators only. Normal users can never access these endpoints. Administrators may suspend/reactivate normal users; the current endpoint intentionally refuses to suspend other administrators.

The next administrative domain will add dispute cases, reports, evidence/audit events and explicit resolution actions on top of this role foundation.
