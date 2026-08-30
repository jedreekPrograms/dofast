# Email verification

Local-password accounts can require proof of ownership of their email address before password login. The production profile enables this requirement and SMTP delivery; local/CI defaults keep enforcement disabled so development does not depend on an external mailbox.

## Security model

- Existing accounts are marked verified by Flyway V52 to avoid a surprise production lockout during rollout.
- New local-password accounts are unverified when production enforcement is enabled.
- Google/Apple accounts are considered email-verified only through their authoritative provider flow; bootstrap administrators are also marked verified.
- Verification credentials are random opaque values. Only a SHA-256 hash is persisted in PostgreSQL.
- A new resend invalidates older active credentials for the same user.
- Tokens are single-use and expire after the configured TTL.
- Verification uses a consistent pessimistic lock order (`user -> email verification token`) to serialize resend/verify races.
- The resend endpoint always returns `202 Accepted` whether the address is unknown, already verified, federated-only, or awaiting verification.
- SMTP delivery happens asynchronously after the issuing transaction commits. Raw tokens and recipient addresses are never written to application logs.
- Production refuses to start with `required=true` unless SMTP delivery, an absolute HTTPS `/verify-email` URL and a sender address are configured.

## Public API

`POST /users/email-verification/resend`

```json
{ "email": "user@example.com" }
```

Always returns `202 Accepted` for a syntactically valid request.

`POST /users/email-verification/verify`

```json
{ "token": "opaque-token-from-email" }
```

Returns `204 No Content` after successful verification. Invalid, expired or replayed credentials fail with the normal business-error response.

## Browser flow

Production emails point to `EMAIL_VERIFICATION_BASE_URL`, normally `https://app.example.com/verify-email`. The web route reads the `token` query parameter, calls the verification API, and exposes a generic resend form when a token is missing or invalid.

## Production configuration

Required by `compose.prod.yaml`:

- `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` (shared mail transport)
- `EMAIL_VERIFICATION_BASE_URL` — absolute HTTPS URL without query/fragment
- `EMAIL_VERIFICATION_FROM_ADDRESS`

Optional tuning:

- `EMAIL_VERIFICATION_TTL_HOURS` (default 24, allowed 1–72)
- `EMAIL_VERIFICATION_RETENTION_DAYS` (default 7, allowed 1–30)
- `EMAIL_VERIFICATION_CLEANUP_INTERVAL_MS` (default 3600000)

The production Spring profile hard-wires `required: true` and `delivery: smtp`; operators cannot accidentally disable verification through an environment override.
