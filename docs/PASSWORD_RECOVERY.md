# Password recovery

## Security model

Password recovery is a public authentication boundary. `POST /users/password/forgot` always returns `202 Accepted` with an empty body for a syntactically valid email. The response never says whether an account exists, whether it supports password login, whether a reset request was suppressed by abuse controls, or whether a message was queued.

Only accounts with `password_login_enabled=true` receive reset links. Federated-only Google/Apple accounts are intentionally ignored by the forgot-password flow and receive the same public response as an unknown email.

Reset credentials are 32 random bytes encoded as URL-safe opaque strings. PostgreSQL stores only their SHA-256 hashes in `auth_password_reset_tokens`; the raw reset token is never persisted or logged. A new request invalidates earlier active reset credentials for the same account. Tokens are single-use and expire after 30 minutes by default; configuration is constrained to 5–60 minutes.

To limit repeated SMTP delivery and reset-token churn, each password-login account has a database-backed request cooldown. The user row is pessimistically locked before the cooldown check, so concurrent forgot requests cannot bypass the limit. Requests received inside the cooldown keep the existing token valid, queue no new email, and still return the same public `202` response. The default cooldown is 60 seconds and production configuration is constrained to 15–900 seconds.

The raw reset credential exists only long enough to move through an in-memory application event. The event is handled with `@TransactionalEventListener(AFTER_COMMIT)` and a dedicated asynchronous executor, so SMTP delivery starts only after the token row commits and does not keep the public forgot-password request waiting on the mail server. This materially reduces account-enumeration timing differences caused by SMTP latency.

SMTP delivery failures log only the internal user id, never the recipient email or raw token. A failed delivery can leave an undisclosed valid token until expiry; a later recovery request outside the cooldown invalidates it before creating a new credential. Retention cleanup removes expired/consumed reset rows.

## Reset transaction

`POST /users/password/reset` accepts the opaque token and a new password. The backend hashes the submitted token and pessimistically locks the matching reset row. An expired, used, invalidated or unknown token is rejected with the same generic invalid/expired-link error.

A successful reset atomically:

1. locks the user;
2. rejects reuse of the current password;
3. writes the new password hash;
4. increments `users.auth_version`;
5. marks the reset token used;
6. invalidates any other active reset tokens for the account;
7. revokes every active refresh session with reason `PASSWORD_RESET`.

The reset token cannot be replayed.

## Immediate access-token invalidation

Access JWTs now carry the user's `auth_version` in the signed `av` claim. `JwtAuthFilter` reloads the current user for every Bearer request and requires the JWT version to equal the database version. Password change and password reset both increment the version.

This means a previously issued access JWT stops authenticating immediately after the credential change; the system no longer relies only on the 10-minute access-token expiry for password-compromise containment. Refresh sessions are separately revoked in the same password-change/reset transaction.

Deploying migration `V51__password_recovery.sql` causes older access JWTs that do not contain the `av` claim to be rejected, effectively requiring a fresh browser-session restoration/login after rollout.

## Browser flow

The public web routes are:

- `/forgot-password` — accepts an email and always displays the generic delivery message after a successful API request;
- `/reset-password?token=...` — validates matching new-password fields in the browser and submits the opaque token to the API.

After a successful reset, the page directs the user back to login and explains that previous sessions have been invalidated.

## Production delivery configuration

Production hard-wires password-recovery delivery to SMTP and supports:

```text
SMTP_HOST=<smtp host>
SMTP_PORT=587
SMTP_USERNAME=<smtp username>
SMTP_PASSWORD=<smtp secret>
PASSWORD_RESET_BASE_URL=https://<trusted doFast origin>/reset-password
PASSWORD_RECOVERY_FROM_ADDRESS=<verified sender address>
PASSWORD_RESET_TTL_MINUTES=30
PASSWORD_RESET_RETENTION_DAYS=7
PASSWORD_RESET_REQUEST_COOLDOWN_SECONDS=60
PASSWORD_RESET_CLEANUP_INTERVAL_MS=3600000
```

The reset base URL must be absolute HTTPS and cannot already contain a query or fragment. The mailer appends only the URL-encoded `token` query parameter.

Local/CI environments default to `PASSWORD_RECOVERY_DELIVERY=disabled`; they never expose a development endpoint that returns reset tokens. Runtime CI seeds a known token hash directly in PostgreSQL to test the public reset boundary without weakening the production API.

The account cooldown is defense-in-depth, not a substitute for edge/IP-aware rate limiting. Global abuse controls should still protect forgot/reset alongside login and registration.

## Validation

The automated tests and runtime gate verify:

- existing and missing emails both return the same `202` empty response in local/CI mode;
- repeated forgot requests inside the account cooldown do not invalidate the current token or queue another email;
- raw reset tokens are absent from PostgreSQL;
- a seeded hash can be consumed exactly once through the public reset endpoint;
- `auth_version` increments;
- a Bearer issued before reset immediately becomes `401`;
- existing refresh authority is revoked;
- the old password stops working and the new password works;
- replaying the reset token fails.
