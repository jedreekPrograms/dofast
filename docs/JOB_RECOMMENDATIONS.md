# Personalized job recommendations

## Purpose

Authenticated users can receive a compact discovery feed based on the concrete service specializations declared on their public profile. Recommendations are intentionally a convenience layer over normal discovery: they never accept work automatically, change availability, reveal additional location data or alter saved-search alerts.

## API

`GET /jobs/recommended?page=0&size=6`

The endpoint is authenticated. It returns:

- `jobs` — the normal application-owned `PageResponse<JobResponse>` envelope;
- `specializationCount` — the number of the caller's active selectable specializations used for matching.

`page` is zero-based. `size` is limited to `1..24` and defaults to `6` for the discovery-page preview.

The endpoint is deliberately not included in the public `GET /jobs` / `GET /jobs/nearby` security exception. Recommendation criteria belong to the authenticated user even though every returned job still uses the normal privacy-safe public `JobResponse` shape.

## Matching rules

The server reads active specialization category IDs from `user_service_categories` introduced by Flyway V33. Only active concrete leaf categories with a fulfillment mode participate. A relation pointing at a category that was later deactivated therefore stops influencing recommendations without requiring destructive profile cleanup.

Recommended jobs must:

- be `OPEN`;
- use one of the caller's active leaf specialization categories;
- belong to another user;
- have no block relation in either direction between the caller and the job owner.

Filtering is performed in the database before pagination, so blocked or own jobs cannot create short or misleading pages after an in-memory filter. Results are ordered by creation time descending and then job id descending, matching normal paginated discovery.

If the user has no active specializations, the service returns an empty page immediately and does not execute a jobs query. `specializationCount = 0` lets the web client show a profile-setup call to action instead of pretending that the marketplace simply has no matching jobs.

## Privacy and trust boundaries

Recommendations return exactly the same public job DTO used by ordinary discovery. They do not expose:

- exact PostGIS coordinates;
- participant-only addresses;
- route geometry;
- live tracking;
- chat history;
- wallet, payment or escrow data;
- saved-search center points;
- private verification or moderation information.

Public location labels retain their existing semantics. Personalization is category-based only in this slice; the free-form `publicLocation` profile label is not parsed or geocoded and is never used as a hidden location signal.

## Web experience

For authenticated users the main jobs page shows a `Dla Ciebie` section above the public filters. It displays up to six newest matching open jobs and links directly to profile settings so the user can adjust specializations.

The recommendation preview has distinct loading, error and empty states:

- no specializations -> explain how to configure them;
- configured specializations but no matches -> explain that there are currently no open matching jobs;
- request failure -> keep the normal public discovery page usable.

Recommendation cards reuse the standard `JobCard`, including reporting, acceptance and saved-job controls. Bookmark hydration uses the existing batch status endpoint, and one page-level saved-id set keeps duplicate cards synchronized when the same job appears in both the personalized preview and ordinary discovery.

## Schema and performance

No new Flyway migration is required. Matching reuses:

- `user_service_categories` and its user/category indexes from V33;
- the indexed `jobs.category_id` relationship;
- existing job status/discovery indexes;
- the `user_blocks` uniqueness/indexing used by block-aware discovery.

The query receives at most ten category IDs because profile specialization selection is already capped at ten.

## Validation

Unit coverage verifies that:

- a user with no active specializations produces an empty response without querying jobs;
- active specialization IDs and the authenticated viewer ID are passed to the recommendation query;
- recommendation pagination uses deterministic newest-first ordering.

Repository startup/runtime smoke validates the JPQL query and application wiring together with the rest of the PostgreSQL-backed stack. Frontend CI validates dependency audit, lint and production build.
