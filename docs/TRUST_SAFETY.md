# Trust and safety

## Job reports

Authenticated users can report suspicious or prohibited job listings through `POST /job-reports/jobs/{jobId}` with one of the supported structured reasons and an optional note up to 1000 characters.

Reports are private moderation data. They are never exposed through public discovery, job details, saved searches, notifications, or participant location APIs. A user cannot report their own job, and the database plus service layer allow at most one report per reporter and job.

`GET /job-reports/mine` returns only reports created by the authenticated user so the client can show submission history/status without exposing other reporters or moderation activity.

## Admin moderation

Endpoints under `/admin/job-reports` are protected by the existing `ROLE_ADMIN` security boundary. `GET /admin/job-reports` returns a paginated oldest-first moderation queue and can be filtered by report status. The response contains moderation-relevant report metadata but no job location, route geometry or live-tracking data.

`PATCH /admin/job-reports/{id}` accepts a terminal `REVIEWED` or `DISMISSED` decision plus an optional moderation note. Every decision records the moderator and review timestamp. Reports cannot be moved back to `SUBMITTED` or resolved twice; an optimistic-lock version column protects concurrent moderation updates.

The web admin panel exposes this workflow at `/admin/job-reports`. It defaults to pending reports, supports status filtering and pagination, shows only the moderation-safe response fields, and lets an administrator record one terminal decision with an optional internal note. The main admin dashboard also surfaces the current pending-report count.

## Explicit enforcement

A moderation decision does not itself delete a job, suspend a user or modify escrow. Enforcement is an explicit, separately audited admin operation.

`POST /admin/job-reports/{id}/enforcement` currently supports `CANCEL_OPEN_JOB`. It is accepted only after the report has been confirmed as `REVIEWED`, only once per report, and only while the reported job is still `OPEN`. The action uses the normal job cancellation state transition, which removes the listing from open discovery without touching an active assignment, live tracking or escrow.

Every enforcement writes an immutable audit record containing the report, affected job, moderator, action, optional reason and timestamp. Active (`IN_PROGRESS` or later) jobs are deliberately rejected by this endpoint because sanctions that can affect participant funds or an active service require a separate, stronger workflow.
