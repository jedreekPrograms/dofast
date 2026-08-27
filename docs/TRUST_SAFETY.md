# Trust and safety

## Job reports

Authenticated users can report suspicious or prohibited job listings through `POST /job-reports/jobs/{jobId}` with one of the supported structured reasons and an optional note up to 1000 characters.

Reports are private moderation data. They are never exposed through public discovery, job details, saved searches, notifications, or participant location APIs. A user cannot report their own job, and the database plus service layer allow at most one report per reporter and job.

`GET /job-reports/mine` returns only reports created by the authenticated user so the client can show submission history/status without exposing other reporters or moderation activity.

## Admin moderation

Endpoints under `/admin/job-reports` are protected by the existing `ROLE_ADMIN` security boundary. `GET /admin/job-reports` returns a paginated oldest-first moderation queue and can be filtered by report status. The response contains moderation-relevant report metadata but no job location, route geometry or live-tracking data.

`PATCH /admin/job-reports/{id}` accepts a terminal `REVIEWED` or `DISMISSED` decision plus an optional moderation note. Every decision records the moderator and review timestamp. Reports cannot be moved back to `SUBMITTED` or resolved twice; an optimistic-lock version column protects concurrent moderation updates.

The web admin panel exposes this workflow at `/admin/job-reports`. It defaults to pending reports, supports status filtering and pagination, shows only the moderation-safe response fields, and lets an administrator record one terminal decision with an optional internal note. The main admin dashboard also surfaces the current pending-report count.

## Explicit job enforcement

A moderation decision does not itself delete a job, suspend a user or modify escrow. Enforcement is an explicit, separately audited admin operation.

`POST /admin/job-reports/{id}/enforcement` currently supports `CANCEL_OPEN_JOB`. It is accepted only after the report has been confirmed as `REVIEWED`, only once per report, and only while the reported job is still `OPEN`. The action uses the normal job cancellation state transition, which removes the listing from open discovery without touching an active assignment, live tracking or escrow.

`GET /admin/job-reports/{id}/enforcement` returns the persisted enforcement audit for the selected report, or `204 No Content` when no enforcement has been recorded. This lets the admin UI restore the durable enforcement state after a reload without duplicating actions or exposing that audit outside the admin boundary.

The moderation panel presents enforcement only after a report is confirmed. It clearly separates the irreversible `CANCEL_OPEN_JOB` control from the review decision, warns that active jobs are protected, accepts an optional internal reason, and replaces the control with the persisted enforcement audit once the action succeeds.

Every job enforcement writes an immutable audit record containing the report, affected job, moderator, action, optional reason and timestamp. Active (`IN_PROGRESS` or later) jobs are deliberately rejected by this endpoint because sanctions that can affect participant funds or an active service require a separate, stronger workflow.

## Explicit account enforcement

`POST /admin/job-reports/{id}/account-enforcement` supports the separately audited `SUSPEND_JOB_OWNER` action for a confirmed `REVIEWED` report. The target is derived from the reported job on the server; the client cannot choose an arbitrary user id. The action cannot suspend the acting moderator or an administrator account, and a second account sanction for the same report is rejected.

Before suspension, the service checks whether the target participates as requester or contractor in any `IN_PROGRESS`, `COMPLETION_REQUESTED` or `DISPUTED` job. If so, suspension is rejected so an account cannot be locked out while escrow, completion confirmation, dispute handling or live tracking still requires that participant.

When suspension is safe, all remaining `OPEN` jobs created by that account are cancelled through the normal job state transition and the user status changes to `SUSPENDED`. Existing authentication enforcement already refuses new password/Google/Apple logins for suspended accounts and the JWT filter stops authenticating previously issued tokens on subsequent requests, so the sanction takes effect without relying on token expiry.

`GET /admin/job-reports/{id}/account-enforcement` returns the immutable account-enforcement audit or `204 No Content` when none exists. The audit stores the report, target user, moderator, action, optional internal reason and timestamp and remains behind the admin-only boundary. It does not expose location, route or tracking data.

The web moderation panel loads job and account enforcement audits independently. For a confirmed report with no account sanction it offers a separate `SUSPEND_JOB_OWNER` control with an optional internal reason and an explicit warning that the action suspends login access and cancels the target's remaining open listings. If the backend rejects the action because the target has protected active work or is an administrator, the panel surfaces that error without changing either audit state. After a successful suspension, the destructive control is replaced by the persisted target user, moderator, timestamp and reason so a reload cannot make the sanction look repeatable.
