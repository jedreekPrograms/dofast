# Security baseline

This document defines minimum engineering rules for doFast while the product is developed toward handling real users and money.

## Secrets

- Never commit API keys, JWT secrets, passwords or webhook secrets.
- `.env` is local-only; `.env.example` contains placeholders/documentation only.
- Production secrets must come from the deployment platform's secret store.
- Rotate any credential immediately if it is exposed in source control or logs.

## Authentication

- Passwords are stored only as adaptive password hashes.
- JWT signing material must be externally configured and high entropy.
- Authentication failures must not reveal whether an account exists.
- Authorization is enforced server-side for every mutating resource operation.
- User-scoped private state operations must establish a persisted authenticated actor identity before loading or mutating dependent resources; missing or transient identities fail closed before persistence access.
- Saved-search list/create/update/delete operations and private saved-search result reads must reject missing or transient actor identities before accessing saved-search, category or result persistence.
- Private chat conversation lists, message history, sends and read-state mutations must reject missing or transient actor identities before accessing jobs, messages, read state, block state, notifications or realtime delivery.
- Job-report creation, withdrawal and private history are user-scoped operations and must reject missing or transient reporter identities before reading either jobs or report persistence.
- Sensitive resource reads must be scoped to the authenticated actor in the repository/query whenever practical, so unauthorized rows are not loaded before authorization is established; outsider failures should remain neutral and must not trigger dependent sensitive reads.
- Admin-only service operations that expose sensitive queues, evidence or financial state must validate the admin principal themselves and must not rely solely on URL-level security matchers.

## Money and webhooks

- Stripe webhook signatures must be verified before processing events.
- Provider event IDs/payment intent IDs must be idempotent in storage.
- Wallet changes require an auditable ledger entry and transactional consistency.
- A domain state transition that depends on a wallet mutation must verify that the corresponding idempotent ledger operation was newly applied; an already-applied/mismatched operation is a fail-closed conflict, not permission to advance authoritative escrow, publication, payout or refund state.
- User payout eligibility, history, request, cancellation and Stripe Connect recipient-onboarding paths must reject missing or transient actor identities before querying payout, verification, wallet, provider or user state or provisioning external payout accounts.
- User-triggered Stripe top-up creation and refund request/read endpoints must reject missing or transient actor identities before invoking payment or refund services; absent identity must never degrade into an NPE at the financial boundary.
- Monetary values use decimal types; floating-point types are prohibited.

## Application surface

- Production CORS is allowlisted, not wildcarded.
- Security headers are applied at the edge gateway.
- Actuator exposes only explicitly allowed endpoints.
- Error responses must not expose stack traces or secrets.
- Dependencies are scanned/updated continuously by CI automation.

## Before accepting real customer funds

A dedicated security review is required for authentication/session design, authorization, payment flows, replay/idempotency, race conditions, account recovery, abuse prevention, logging/PII, data retention, backup/restore and incident response.
