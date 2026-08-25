# Carlisle Reviews & Trust

## Purpose

Reviews are a trust signal tied to a real, completed doFast job. They are not free-form profile endorsements and cannot be created between arbitrary accounts.

## Review rules

- A review can be submitted only when the job status is `DONE`.
- Only the requester (`createdBy`) and the assigned worker (`takenBy`) can review that job.
- The API derives the reviewed user from the authenticated reviewer and the job participants. Clients never choose an arbitrary `reviewedUserId`.
- Each participant can submit at most one review per job.
- Both sides can review each other, so a completed job can have up to two reviews.
- Rating is an integer from 1 to 5.
- Comment is optional and limited to 2000 characters.
- A database unique constraint on `(job_id, reviewer_id)` protects against concurrent duplicate submissions.
- A database check constraint prevents self-reviews.

## Public trust profile

`GET /users/{id}/profile` is public and exposes only trust-safe information:

- user id,
- nickname,
- average received rating,
- number of received reviews,
- completed jobs as requester,
- completed jobs as worker,
- total completed jobs.

It does not expose email, credentials, private location data or account administration metadata.

`GET /reviews/users/{id}` returns paginated reviews received by that user. Each review includes the public reviewer nickname, job title, rating, comment and creation time.

## Authenticated review API

- `POST /reviews` — submit a review for a completed job.
- `GET /reviews/jobs/{jobId}/eligibility` — tells the current participant whether they can still review and identifies the counterpart.

## Notifications

A successful review creates a durable `REVIEW_RECEIVED` notification for the reviewed user. Realtime delivery is emitted only after the database transaction commits, using the existing notification delivery infrastructure.

## Concurrency and integrity

The job row is acquired through the existing pessimistic `findByIdForUpdate` path before a review is created. The service checks for an existing review and the database independently enforces the unique `(job_id, reviewer_id)` constraint. A concurrent duplicate therefore resolves as `409 Conflict` instead of creating two reputation events.

## UI

Completed jobs in **Moje zlecenia** expose an **Oceń współpracę** action. The review dialog checks eligibility from the backend before submission. Public trust profiles are available at `/users/{userId}` and are linked from job cards and participant views.
