# Identity verification

## Purpose

Identity verification is a trust signal for the doFast marketplace. It is deliberately separate from account authentication: a user can have a valid doFast account without having a verified identity.

The public contract is intentionally minimal. Other users only learn whether the identity is currently verified. Internal provider metadata, rejection reasons and the audit trail are never exposed through the public profile endpoint.

## Data minimization

doFast does **not** store document scans, document numbers, selfies or biometric material in the identity-verification tables. The application stores only:

- the doFast user id;
- the verification status;
- a provider code and optional opaque provider reference;
- timestamps for request, review, verification and revocation;
- the reviewing administrator id for manual decisions;
- a bounded decision reason for rejection/revocation;
- an append-only transition audit trail.

When an external KYC provider is integrated, sensitive evidence should remain with that provider under the applicable retention policy. The doFast database should keep only the opaque provider reference needed to correlate provider events with the local case.

## State machine

`NOT_STARTED` is an API-only state and is not persisted. A stored case follows:

```text
(no case)
   |
   | request
   v
PENDING -------- approve --------> VERIFIED
   |                                  |
   | reject                           | revoke
   v                                  v
REJECTED -------------------------> REVOKED
   |                                  |
   +--------- resubmit ---------------+
                    |
                    v
                 PENDING
```

A repeated request while `PENDING` is idempotent. A `VERIFIED` case cannot be requested again until it has been revoked. Rejected and revoked cases may be resubmitted.

## Authorization and concurrency

- `/verification/**` requires an authenticated user, and the service rejects a missing or transient principal before any case/user repository access.
- `/admin/verifications/**` is covered by the existing `ROLE_ADMIN` rule for `/admin/**`; the service independently requires a persisted `ADMIN` principal before reading the queue or audit events and before locking a case for decision.
- an administrator cannot decide their own verification case;
- the user row is pessimistically locked while a verification request is created/resubmitted, preventing duplicate cases under concurrent requests;
- the verification case is pessimistically locked while an administrator makes a decision;
- the table additionally has a unique constraint on `user_id` and optimistic `@Version` metadata.

## Provider boundary

`VerificationProvider` is the application-facing adapter. Carlisle currently ships `ManualReviewVerificationProvider`, which creates a manual-review case and does not receive or persist identity documents.

A future provider integration should implement the same adapter and return an opaque `providerReference`. Provider callbacks must be signature-verified and idempotent before they are allowed to change the local state machine.

## Admin decisions

Administrators can:

- approve a `PENDING` case;
- reject a `PENDING` case with a meaningful reason;
- revoke a `VERIFIED` case with a meaningful reason;
- inspect the chronological audit trail.

Every state transition creates an `identity_verification_events` record. Negative-decision reasons are visible to the affected user in their authenticated verification screen but are not part of the public profile.

## Trust badge

`GET /users/{id}/profile` exposes only `identityVerified: boolean`. The web client renders a verified-identity badge only when this value is `true`. Revocation removes the badge automatically because the value is derived from the current verification state.

## Notifications

Approval, rejection and revocation use the existing notification domain and realtime delivery path. No separate verification-specific message system is introduced.

## Database migration

`V10__identity_verification.sql` owns the schema for verification cases and their audit events. Flyway remains the only schema migration mechanism.
