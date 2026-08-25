# Job discovery

Carlisle introduces the first scalable public discovery contract for open doFast jobs.

## Public endpoint

`GET /jobs`

Supported query parameters:

- `query` — optional text query, maximum 100 characters;
- `minPrice` — optional non-negative minimum reward;
- `maxPrice` — optional non-negative maximum reward;
- `page` — zero-based page index, default `0`;
- `size` — page size from `1` to `50`, default `20`.

Results are ordered deterministically by newest creation time and then by descending job id.

## Response envelope

The API deliberately returns an application-owned pagination DTO instead of serializing Spring Data's `Page` implementation directly.

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
- public location label.

Only `OPEN` jobs participate in public discovery.

Exact coordinates and the private location label are never part of the discovery response.

## Database indexing

Flyway migration `V4__job_discovery_indexes.sql` enables PostgreSQL `pg_trgm` and adds partial indexes limited to `OPEN` jobs:

- GIN trigram index on lower-cased title;
- GIN trigram index on lower-cased description;
- GIN trigram index on lower-cased public location label;
- B-tree index on price, creation time and id.

The indexes are designed around the actual public search workload instead of indexing historical completed/cancelled jobs unnecessarily.

## Validation

The API rejects:

- negative prices;
- `minPrice > maxPrice`;
- negative page indexes;
- page sizes outside `1..50`;
- overlong text queries;
- malformed request-parameter types.

Validation errors use the shared API error contract and return HTTP 400.

## Web client

The jobs page consumes the same paginated endpoint and provides:

- text search;
- minimum and maximum reward filters;
- loading, error and empty states;
- responsive job cards;
- previous/next pagination controls;
- cancellation of obsolete fetch requests when filters change.

Carlisle is an internal technical milestone name and is intentionally not exposed in customer-facing UI text.

## Verification

CI verifies:

- Maven unit tests;
- frontend lint/build and production dependency audit;
- PostGIS and `pg_trgm` availability;
- Flyway migrations;
- real job creation with escrow;
- paginated search metadata;
- text and price filtering;
- invalid price-range rejection;
- privacy of exact/private location data;
- nearby matching;
- Nginx API gateway behavior.
