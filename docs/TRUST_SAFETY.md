# Trust and safety

## Job reports

Authenticated users can report suspicious or prohibited job listings through `POST /job-reports/jobs/{jobId}` with one of the supported structured reasons and an optional note up to 1000 characters.

Reports are private moderation data. They are never exposed through public discovery, job details, saved searches, notifications, or participant location APIs. A user cannot report their own job, and the database plus service layer allow at most one report per reporter and job.

`GET /job-reports/mine` returns only reports created by the authenticated user so the client can show submission history/status without exposing other reporters or moderation activity.

A reporter may withdraw their own report through `POST /job-reports/{reportId}/withdraw`, but only while the report is still `SUBMITTED`. Withdrawal is not implemented as deletion: the report moves to `WITHDRAWN` and stores `withdrawnAt`, preserving a durable private history entry without leaving it in the pending moderation queue. Once moderation has produced `REVIEWED` or `DISMISSED`, withdrawal is rejected. The moderation API is also explicitly restricted to `REVIEWED` and `DISMISSED`, so administrators cannot manufacture a `WITHDRAWN` state.

The web discovery cards expose a `Zgłoś ofertę` action only to authenticated users viewing somebody else's job. The modal sends only the structured reason and optional note, warns users not to include sensitive payment data, and explains that a report enters moderation rather than automatically removing a listing or sanctioning an account. After a successful submission the current card is marked as reported; duplicate and own-job attempts remain enforced by the backend.

The authenticated `/my-reports` page uses only reporter-scoped endpoints. It shows the reporter's own reason, optional note, submission time, coarse moderation status, the resolution timestamp once moderation reaches a terminal decision, and the withdrawal timestamp for withdrawn reports. Pending reports expose a withdrawal control; terminal or already-withdrawn reports do not. The page does not expose moderator identities, internal moderation notes, enforcement audits or another reporter's data. It also keeps reports separate from payment/escrow disputes and directs transaction problems to the dedicated dispute workflow.

When moderation reaches a terminal decision, the reporter receives a durable `JOB_REPORT_REVIEWED` or `JOB_REPORT_DISMISSED` notification linked to the reported job. The notification only communicates the coarse outcome. It never contains the moderator identity, internal moderation note, enforcement reason or whether a later job/account sanction was executed. Withdrawn reports do not generate a moderation decision notification.

## Admin moderation

Endpoints under `/admin/job-reports` are protected by the existing `ROLE_ADMIN` security boundary. `GET /admin/job-reports` returns a paginated oldest-first moderation queue and can be filtered by report status. The response contains moderation-relevant report metadata but no job location, route geometry or live-tracking data.

`PATCH /admin/job-reports/{id}` accepts only terminal `REVIEWED` or `DISMISSED` decisions plus an optional moderation note. Every decision records the moderator and review timestamp. Reports cannot be moved back to `SUBMITTED`, changed to `WITHDRAWN` by an administrator, or resolved twice; an optimistic-lock version column protects concurrent moderation updates. A reporter withdrawal racing with moderation therefore resolves through the existing state/version protections rather than silently overwriting a decision.

The web admin panel exposes this workflow at `/admin/job-reports`. It defaults to pending reports, supports status filtering and pagination, shows only the moderation-safe response fields, and lets an administrator record one terminal decision with an optional internal note. The main admin dashboard also surfaces the current pending-report count.

## Explicit job enforcement

A moderation decision does not itself delete a job, suspend a user or modify escrow. Enforcement is an explicit, separately audited admin operation.

`POST /admin/job-reports/{id}/enforcement` currently supports `CANCEL_OPEN_JOB`. It is accepted only after the report has been confirmed as `REVIEWED`, only once per report, and only while the reported job is still `OPEN`. The action uses the normal job cancellation state transition, which removes the listing from open discovery without touching an active assignment, live tracking or escrow.

`GET /admin/job-reports/{id}/enforcement` returns the persisted enforcement audit for the selected report, or `204 No Content` when no enforcement has been recorded. This lets the admin UI restore the durable enforcement state after a reload without duplicating actions or exposing that audit outside the admin boundary.

The moderation panel presents enforcement only after a report is confirmed. It clearly separates the irreversible `CANCEL_OPEN_JOB` control from the review decision, warns that active jobs are protected, accepts an optional internal reason, and replaces the control with the persisted enforcement audit once the action succeeds.

Every job enforcement writes an immutable audit record containing the report, affected job, moderator, action, optional reason and timestamp. Active (`IN_PROGRESS` or later) jobs are deliberately rejected by this endpoint because sanctions that can affect participant funds or an active service require a separate, stronger workflow.

## Explicit account enforcement

`POST /admin/job-reports/{id}/account-enforcement` supports the separately audited `SUSPEND_JOB_OWNER` action for a confirmed `REVIEWED` report. The target is derived from the reported job on the server; the client cannot choose an arbitrary user id. The action cannot suspend the acting moderator or an administrator account, and a second account sanction for the same report is rejected.

Before ordinary suspension, the service checks whether the target participates as requester or contractor in any `IN_PROGRESS`, `COMPLETION_REQUESTED` or `DISPUTED` job. If so, ordinary suspension is rejected so an account cannot be locked out while escrow, completion confirmation, dispute handling or live tracking still requires that participant.

When ordinary suspension is safe, all remaining `OPEN` jobs created by that account are cancelled through the normal job state transition and the user status changes to `SUSPENDED`. Existing authentication enforcement refuses new password/Google/Apple logins for suspended accounts and the JWT filter stops authenticating previously issued tokens on subsequent requests.

`GET /admin/job-reports/{id}/account-enforcement` returns the immutable account-enforcement audit or `204 No Content` when none exists. The audit stores the report, target user, moderator, action, optional internal reason and timestamp and remains behind the admin-only boundary. It does not expose location, route or tracking data.

The generic `/admin/users/{id}/status` endpoint is deliberately not a suspension path. Requests attempting to set `SUSPENDED` are rejected so administrators cannot bypass the reviewed-report requirement, active-job safeguards, open-listing cleanup or immutable enforcement audit. The generic admin users screen only exposes reactivation for an already suspended non-admin account; active accounts direct moderators to the report-enforcement workflow for sanctions.

## Emergency account enforcement

For an immediate fraud or safety risk where waiting for active work to finish would be unsafe, the same reviewed-report endpoint additionally supports `EMERGENCY_SUSPEND_JOB_OWNER`. This is an explicit operator choice; it is not the default and the web moderation panel keeps it visually separate from ordinary suspension.

The emergency path is fail-closed and transactional. It first pessimistically locks the target user, then all protected participant jobs in deterministic job-id order. Every `IN_PROGRESS` or `COMPLETION_REQUESTED` job must still have `HELD` escrow before it is converted into a `SAFETY_CONCERN` dispute. The dispute is created directly in `UNDER_REVIEW`, assigned to the moderator executing the sanction, and receives normal dispute audit events. No refund or worker payout happens automatically; money remains held until the existing admin dispute-resolution workflow explicitly decides the outcome.

A job already in `DISPUTED` is not duplicated. The emergency path requires a matching active dispute record and held escrow. A `DISPUTED` job without an active dispute, a missing escrow hold, or another inconsistent protected state aborts the whole account sanction. Because all containment changes share the transaction, the system does not intentionally leave a user suspended while only some active jobs were protected.

For every protected job, live tracking is stopped and the persisted current location/ETA state is cleared before the account sanction completes. Remaining `OPEN` jobs created by the target are then cancelled. Finally the user's authentication version is incremented, all active refresh sessions are revoked with the emergency-suspension reason, and the account moves to `SUSPENDED`. Incrementing the authentication version prevents a pre-sanction access token from becoming valid again after a later reactivation.

The internal moderation reason remains only in the account-enforcement audit. Participants receive a generic safety-dispute description and notification stating that moderation has paused the job and funds remain protected; the private enforcement reason is not copied into participant-visible dispute data.

Already submitted external payout operations cannot be reversed merely by changing account status. Existing payout reconciliation remains responsible for provider-side terminal outcomes. The emergency path prevents the sanction from pretending that money already submitted to an external provider has been clawed back.

The dedicated `Emergency account enforcement smoke` exercises the real API and PostgreSQL lifecycle: ordinary suspension is rejected for active work, emergency enforcement creates the safety dispute, escrow remains `HELD`, open listings are cancelled, live tracking is cleared, refresh sessions are revoked, the old access token is rejected, and the unaffected counterparty can still see the resulting dispute.
