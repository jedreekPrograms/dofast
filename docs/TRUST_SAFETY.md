# Trust and safety

## Job reports

Authenticated users can report suspicious or prohibited job listings through `POST /job-reports/jobs/{jobId}` with one of the supported structured reasons and an optional note up to 1000 characters.

Reports are private moderation data. They are never exposed through public discovery, job details, saved searches, notifications, or participant location APIs. A user cannot report their own job, and the database plus service layer allow at most one report per reporter and job.

`GET /job-reports/mine` returns only reports created by the authenticated user so the client can show submission history/status without exposing other reporters or moderation activity.

Initial statuses are `SUBMITTED`; `REVIEWED` and `DISMISSED` are reserved for a later admin moderation workflow. This slice intentionally does not auto-hide jobs or penalize users based on a single report.
