# Trust and safety

## Job reports

Authenticated users can report suspicious or prohibited job listings through `POST /job-reports/jobs/{jobId}` with one of the supported structured reasons and an optional note up to 1000 characters.

Reports are private moderation data. They are never exposed through public discovery, job details, saved searches, notifications, or participant location APIs. A user cannot report their own job, and the database plus service layer allow at most one report per reporter and job.

`GET /job-reports/mine` returns only reports created by the authenticated user so the client can show submission history/status without exposing other reporters or moderation activity.

## Admin moderation

Endpoints under `/admin/job-reports` are protected by the existing `ROLE_ADMIN` security boundary. `GET /admin/job-reports` returns a paginated oldest-first moderation queue and can be filtered by report status. The response contains moderation-relevant report metadata but no job location, route geometry or live-tracking data.

`PATCH /admin/job-reports/{id}` accepts a terminal `REVIEWED` or `DISMISSED` decision plus an optional moderation note. Every decision records the moderator and review timestamp. Reports cannot be moved back to `SUBMITTED` or resolved twice; an optimistic-lock version column protects concurrent moderation updates.

A moderation decision intentionally does not automatically delete a job, suspend a user or modify escrow. Enforcement actions should remain explicit, separately authorized operations with their own audit trail rather than side effects of a single report review.
