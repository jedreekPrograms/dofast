# Exact location lifecycle access

## Purpose

Exact execution location is sensitive job data. Public discovery exposes only coarse labels; exact coordinates, private address labels and route geometry exist only to prepare and execute a job.

This boundary is independent from live courier tracking. Live tracking already clears the current worker position when tracking stops. This policy defines when authenticated marketplace participants may read the exact persisted job origin/route through the job API.

## Access policy

The requester may read exact location while the job is `OPEN` so they can verify the execution details they published before a worker is selected.

After assignment, exact location and route access is limited to the requester and assigned worker while the job is in an execution/evidence state:

- `IN_PROGRESS`;
- `COMPLETION_REQUESTED`;
- `DISPUTED`.

Once a job reaches either terminal marketplace state, exact-location API access closes for both participants:

- `DONE`;
- `CANCELLED`.

Public job DTOs remain unchanged and never expose exact coordinates, private labels or encoded route geometry.

## Why terminal access closes

A completed or cancelled task no longer requires exact coordinates for execution. Keeping participant-facing historical access indefinitely would turn an operational execution field into a long-lived location-history feature without a product need.

Disputed jobs deliberately retain participant access while the dispute is active because location can be relevant evidence. A dispute resolution that completes or cancels the job closes participant exact-location access together with the terminal lifecycle state.

## Persistence and retention boundary

This slice changes API authorization only. Existing exact job execution fields remain persisted after a terminal state so financial, legal and support retention can be designed deliberately rather than deleted opportunistically inside a money-sensitive lifecycle transaction.

A separate retention policy must define when persisted exact origin/destination coordinates, private address labels, route-stop execution data, place identifiers and encoded route geometry are anonymized or purged after the applicable support/legal retention period. That future purge must preserve public/coarse labels and accounting/job history that do not require exact location.

Until that retention policy is implemented, exact terminal data is server-side only and unavailable through normal participant APIs.

## Verification

Focused service tests verify that:

- the requester can inspect their own exact location before assignment;
- requester access remains available during an active dispute;
- requester access is denied after `DONE`;
- requester access is denied after `CANCELLED`;
- assigned-worker access is denied after `CANCELLED`;
- the existing worker-after-completion denial remains covered by the broader job service tests.

The normal Maven verification, frontend lint/build and container/runtime smoke suite remain required merge gates for changes to this boundary.
