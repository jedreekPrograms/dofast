# HTTP authorization matrix

This document is the reviewed authorization contract for the doFast HTTP surface. It is enforced at three layers:

1. `HttpAuthorizationPolicy` contains the closed anonymous and admin path allowlists used by Spring Security.
2. Every private controller carries the authenticated `User` into the application boundary, except the one explicitly documented actorless read below.
3. Services and scoped repository queries enforce resource ownership, participation or administrator privileges before returning sensitive state or causing side effects.

Anything not listed as anonymous or administrator-only is authenticated by default. Adding a controller method without an explicit boundary fails `HttpAuthorizationPolicyTest`.

## Anonymous HTTP surface

The allowlist is method-specific. A path listed for `GET` is not automatically public for any other HTTP method.

| Method | Path | Purpose and downstream boundary |
|---|---|---|
| `POST` | `/users` | Account registration; validated request DTO. |
| `POST` | `/users/login` | Password authentication; rate limited. |
| `POST` | `/users/login/google` | Google authentication; provider token validation and rate limiting. |
| `POST` | `/users/login/apple` | Apple authentication; signed identity token and challenge validation. |
| `POST` | `/users/login/apple/challenge` | Short-lived Apple login challenge creation. |
| `POST` | `/users/session/refresh` | Refresh-cookie rotation and session-version validation. |
| `POST` | `/users/session/logout` | Refresh-session revocation and cookie clearing. |
| `POST` | `/users/password/forgot` | Enumeration-resistant recovery request. |
| `POST` | `/users/password/reset` | Single-use recovery token validation. |
| `POST` | `/users/email-verification/resend` | Enumeration-resistant verification resend. |
| `POST` | `/users/email-verification/verify` | Single-use verification token validation. |
| `POST` | `/webhooks/stripe` | Stripe-signature verification before event processing; no other method is public. |
| `GET` | `/jobs` | Sanitized public job discovery. |
| `GET` | `/jobs/nearby` | Sanitized and rate-limited public job discovery. |
| `GET` | `/job-categories` | Public category catalogue. |
| `GET` | `/users/{id}/profile` | Deliberately limited public profile projection. |
| `GET` | `/reviews/users/{userId}` | Public received-review projection. |

## Transport and health exceptions

| Path | HTTP perimeter | Effective authorization |
|---|---|---|
| `/ws`, `/ws/**`, `/ws-sockjs/**` | The transport handshake is public. | STOMP `CONNECT` requires a valid bearer token and current persisted account; subscriptions are authorized per chat/tracking destination, and client `SEND` is denied. |
| `/actuator/health`, `/actuator/health/**` | Public for infrastructure probes. | Only the configured health exposure is available; other actuator paths remain authenticated. |

## Authenticated user surface

| Path family | Authorized actor | Resource rule |
|---|---|---|
| `/users/me/**`, `/users/me/service-area`, `/user-blocks/**` | Current persisted user | Only the actor's account, categories, service area and block relationships. |
| Private `/jobs/**` reads and lifecycle commands | Requester or assigned worker, depending on state | Exact location, route, attachments, completion and cancellation state are participant-scoped; public discovery uses sanitized projections. |
| `/saved-jobs/**`, `/saved-searches/**` | Current persisted user | Every list, status, result and mutation is scoped to the owner. |
| `/jobs/{jobId}/proposals/**` | Proposer or job requester, depending on operation | Proposal details, deletion, acceptance and funding are scoped before dependent financial access. |
| `/jobs/publications/**` | Publication owner | Pending publications, payment intent creation and cancellation are owner-scoped. |
| `/jobs/{jobId}/cancellation/**` | Job participant | Request, approval, decline and withdrawal follow role and job-state rules. |
| `/jobs/{jobId}/attachments/**` | Authorized job participant | Metadata and content reads use participant-scoped lookups; upload/delete follow job role and state. |
| `/jobs/{jobId}/expenses/**` | Authorized job participant | Claims and evidence are visible only inside the job relationship. |
| `/jobs/{jobId}/tracking/**` | Authorized job participant | Reads and tracking transitions enforce participant role and job state. |
| `/routing/quotes/**` | Quote owner | Creation establishes a persisted owner before parsing/provider work; reads use owner-scoped queries. |
| `/chat/**` | Authorized job participant | Conversation list, history, send and read state require a persisted actor and job participation; blocking is enforced for interaction. |
| `/job-reports/**` | Reporter | Creation, withdrawal and history are reporter-scoped; reported targets come from the job. |
| `/disputes/**` | Dispute participant | Creation and historical reads are scoped to the job parties; outsider failures are neutral. |
| `/notifications/**` | Notification owner | Feed, unread count, preferences and read mutations are scoped to the actor. |
| `/wallet/**`, `/wallet/payouts/**` | Wallet owner | Ledger, eligibility, onboarding, payout requests/history/cancellation are owner-scoped before provider access. |
| `/payments/create-intent`, `/payments/refunds/**` | Payment actor or refund requester | Persisted identity is required before wallet, persistence or Stripe dispatch; refund reads use requester-scoped lookup. |
| `/verification/**` | Verification subject | Current status and requests apply only to the actor. |
| `/reviews`, `/reviews/jobs/{jobId}/eligibility` | Completed-job participant | Eligibility and submission derive reviewer/reviewee from the completed job, not request-supplied identities. |

The complete symmetric block behavior for discovery, new contact, historical evidence and existing commercial relationships is maintained in [USER_BLOCKING.md](USER_BLOCKING.md).

`GET /payments/platform-fee-policy` is the only authenticated HTTP endpoint that intentionally does not carry a `User` parameter. It returns one global, non-user-specific policy value. `GET /payments/platform-fee-quote` does carry the actor because it accepts request data and belongs to the authenticated payment flow.

## Administrator surface

All `/admin/**` endpoints require `ROLE_ADMIN` at the HTTP perimeter and a persisted `ADMIN` principal again at the service boundary before repository or provider access.

| Path family | Sensitive capability |
|---|---|
| `/admin/overview`, `/admin/users/**` | User overview, status changes and reactivation audit. |
| `/admin/job-reports/**` | Moderation queue, enforcement and account enforcement. |
| `/admin/disputes/**` | Dispute queue, evidence/messages, claim and resolution. |
| `/admin/payouts/**` | Payout review, provider events, retry and terminal failure. |
| `/admin/finance/reconciliation` | Financial reconciliation state. |
| `/admin/verifications/**` | Identity-verification queue, evidence events and decisions. |

## Failure semantics

- Missing or transient private actors fail before sensitive repository/provider access.
- An authenticated outsider receives a neutral not-found response where revealing resource existence would create an IDOR oracle.
- A known participant attempting an invalid role/state transition receives forbidden or conflict according to the domain contract.
- Administrator service checks are mandatory even when the URL matcher already requires `ROLE_ADMIN`.

## Change rule

When adding or changing an endpoint, update `HttpAuthorizationPolicy` only if anonymous access is intentional, add the corresponding ownership/service-boundary tests, and update this matrix. The controller-discovery regression test must remain green and its anonymous endpoint count must change deliberately.
