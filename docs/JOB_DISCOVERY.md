# Job discovery

Carlisle introduces the first scalable public discovery contract for open doFast jobs.

## Public endpoints

`GET /jobs`

Supported query parameters:

- `query` — optional text query, maximum 100 characters;
- `category` — optional active category or subcategory slug, maximum 80 characters;
- `minPrice` — optional non-negative minimum reward;
- `maxPrice` — optional non-negative maximum reward;
- `page` — zero-based page index, default `0`;
- `size` — page size from `1` to `50`, default `20`.

Results are ordered deterministically by newest creation time and then by descending job id.

`GET /jobs/nearby`

Supported query parameters:

- `latitude` / `longitude` — required search origin;
- `radiusMeters` — search radius from 100 m to 50 km, default 5 km;
- `category` — optional active category or subcategory slug, using the same semantics as `GET /jobs`;
- `limit` — maximum number of matches from 1 to 100, default 50.

Nearby results remain ordered primarily by geospatial distance and expose only public location labels.

## Category filtering

The public category catalog from `GET /job-categories` is the source of filter values. Discovery accepts stable slugs rather than database ids so links can remain readable and portable.

Filtering by a leaf slug returns that exact subcategory. Filtering by a parent slug returns jobs from all of its direct selectable children, which lets a user browse a broad area such as transport, home services or shopping without manually selecting every leaf. The same rule is applied consistently to paginated and nearby discovery.

The endpoints only accept lowercase URL-safe slugs (`a-z`, digits and hyphens). Unknown but well-formed slugs safely return an empty result set rather than broadening the search.

## Saved jobs

Authenticated users can keep a private shortlist of currently available jobs:

- `PUT /saved-jobs/{jobId}` saves an `OPEN` job and is idempotent;
- `DELETE /saved-jobs/{jobId}` removes the bookmark and is idempotent;
- `GET /saved-jobs/{jobId}/status` returns whether the current user has saved one job;
- `GET /saved-jobs/status?jobIds=1,2,3` returns the subset of up to 50 requested job ids saved by the current user in one query, avoiding per-card N+1 status requests;
- `GET /saved-jobs?page=0&size=20` returns a stable paginated list of the user's saved jobs.

Bookmarks are private to the authenticated user and are protected by a database uniqueness constraint on `(user_id, job_id)`. Users cannot bookmark their own jobs: the server rejects that operation even if a custom client bypasses the web UI. The shortlist is actionable-only; before each paginated read the service removes that user's bookmarks whose jobs are no longer `OPEN`, then returns the remaining open jobs. This prevents accepted, completed or cancelled offers from accumulating as stale rows over time. Foreign keys still cascade bookmark cleanup when a user or job is deleted.

The batch status endpoint accepts only positive ids, requires at least one id and caps the request at 50 ids. Duplicate requested ids are collapsed before the repository lookup. It returns only saved ids, so the web client can initialize bookmark state for an entire discovery page with one authenticated request.

## Saved searches

Authenticated users can persist reusable discovery filters:

- `GET /saved-searches` returns the current user's presets ordered by most recently updated;
- `GET /saved-searches/{id}/results?limit=50` returns private radius-filtered results for one owned preset;
- `POST /saved-searches` creates a preset;
- `PUT /saved-searches/{id}` replaces one owned preset;
- `DELETE /saved-searches/{id}` removes one owned preset.

A preset stores a user-visible name plus optional text query, category, minimum/maximum reward and an optional private center point with radius. At least one criterion is required, price ranges are validated server-side and each account is limited to 20 presets. Names are unique per user case-insensitively, which prevents visually duplicated entries such as `Paczki` and `paczki`.

Radius presets keep their center coordinates behind the authenticated saved-search boundary. The private results endpoint resolves the owned preset server-side and applies text, category, price and PostGIS `ST_DWithin` filtering in one database query. Only normal public job fields plus the computed distance are returned; the stored center coordinates, exact addresses, route geometry and participant-only tracking data are never part of the response. Results are ordered by distance and capped to 1–100 entries.

Saved searches never expose exact coordinates, private address labels, route geometry or participant-only data in shareable discovery URLs or notification bodies. Category values are persisted as foreign keys to the existing catalog and returned as stable public slugs/names. Deleting a user cascades their presets; deleting a category clears only the optional category filter instead of deleting the user's entire preset.

`V21__saved_searches.sql` adds the persistence table and `V23__saved_search_radius.sql` adds the private PostGIS center/radius criteria. The private results read reuses those columns and existing job/category/PostGIS indexes, so no additional schema migration is required.

## Response envelope

The paginated API deliberately returns an application-owned pagination DTO instead of serializing Spring Data's `Page` implementation directly.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

This keeps the external API stable if internal Spring pagination details change later.

## Search behavior

Text search is case-insensitive and matches:

- title;
- description;
- public origin/location label;
- public destination label, when present.

Only `OPEN` jobs participate in public discovery and private saved-search result reads.

Exact coordinates and private location labels are never part of discovery responses. Nearby category filtering is executed server-side against persisted category relationships and does not alter the existing PostGIS privacy boundary.

## Database indexing

Flyway migration `V4__job_discovery_indexes.sql` enables PostgreSQL `pg_trgm` and adds partial indexes limited to `OPEN` jobs:

- GIN trigram index on lower-cased title;
- GIN trigram index on lower-cased description;
- GIN trigram index on lower-cased public location label;
- B-tree index on price, creation time and id.

The indexes are designed around the actual public search workload instead of indexing historical completed/cancelled jobs unnecessarily.

Category filtering uses the existing indexed foreign key from jobs to the category catalog and the unique category slug constraint introduced with the catalog; no additional Flyway migration is required for this slice. Nearby filtering combines those relationships with the existing PostGIS radius predicate.

`V19__saved_jobs.sql` adds the bookmark table, a user/creation-time index for ordered shortlist reads, a job lookup index and the uniqueness constraint that keeps repeated save requests idempotent at the persistence boundary. Saved-job lifecycle cleanup uses the existing indexed user/job relationships and requires no schema migration.

## Validation

The API rejects:

- negative prices;
- `minPrice > maxPrice`;
- negative page indexes;
- page sizes outside `1..50`;
- overlong text/category queries;
- malformed category slugs;
- invalid latitude/longitude, radius or nearby limit;
- private saved-search result limits outside `1..100`;
- private saved-search result reads for a preset without radius criteria;
- empty, oversized or non-positive saved-job batch ids;
- attempts to bookmark the caller's own job;
- saved-search presets with no discovery criteria;
- duplicate saved-search names for one user, case-insensitively;
- more than 20 saved-search presets per user;
- malformed request-parameter types.

Validation errors use the shared API error contract. Input validation returns HTTP 400, while forbidden ownership operations use the shared forbidden-operation response.

## Web client

The jobs page consumes the paginated endpoint and provides:

- text search;
- hierarchical category/subcategory selection from the public catalog;
- broad parent-category filtering and precise leaf filtering;
- minimum and maximum reward filters;
- loading, error and empty states;
- responsive job cards;
- previous/next pagination controls;
- cancellation of obsolete fetch requests when filters change.

Authenticated users can save an open job directly from its discovery card. The `/saved-jobs` route is protected by the normal authentication boundary and provides a paginated shortlist using the same privacy-safe `JobResponse` cards. Removing a bookmark is idempotent; after removal the current page is refreshed, and an emptied trailing page automatically steps back so the user is never stranded on a blank page.

For an authenticated discovery page, the web client hydrates bookmark state with one `GET /saved-jobs/status` batch request after loading the public jobs. Each card therefore reflects the persisted state after reload without issuing N+1 status requests. The same card can toggle the bookmark with the idempotent `PUT`/`DELETE` endpoints and updates the page-level saved-id set immediately after a successful mutation. A bookmark-status lookup failure does not hide otherwise public discovery results.

Applied public discovery filters are mirrored into readable URL query parameters. Presets without a private radius still rebuild that shareable public URL. For radius presets, `/saved-searches` loads results through the authenticated private-results endpoint and renders them inline, so the saved center never has to be copied into the browser URL. The UI shows only the configured radius description, public job labels and computed distance from the private center.

Carlisle is an internal technical milestone name and is intentionally not exposed in customer-facing UI text.

## Verification

CI verifies:

- Maven unit tests, including paginated and nearby category-filter routing and normalization;
- saved-job idempotency, open-job eligibility, own-job rejection, batched status lookup, stale-bookmark cleanup and pagination behavior;
- saved-search normalization, required criteria, price-range validation, duplicate-name rejection, per-user limits, radius validation and private result routing;
- frontend lint/build and production dependency audit;
- PostGIS and `pg_trgm` availability;
- Flyway migrations;
- real job creation with escrow;
- paginated search metadata;
- text, category and price filtering contracts;
- invalid price-range rejection;
- privacy of exact/private location data;
- nearby matching with leaf and parent category filters against PostgreSQL/PostGIS;
- Nginx API gateway behavior.
