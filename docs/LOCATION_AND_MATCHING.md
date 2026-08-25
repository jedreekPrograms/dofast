# Location and nearby matching

## Purpose

doFast matches local tasks with nearby workers. Exact coordinates are therefore useful for routing and task execution, but they are also sensitive data. The location model intentionally separates public discovery data from private execution data.

## Data model

Each newly created job contains:

- `location` — exact WGS84 coordinates stored as PostgreSQL/PostGIS `GEOGRAPHY(POINT,4326)`;
- `location_label` — a deliberately public area label, for example `Wrocław, Plac Grunwaldzki`;
- `location_private_label` — optional private address or access instruction, for example a street address or apartment note.

Existing jobs created before the PostGIS migration may have no location. They remain readable through the normal job API but are excluded from nearby matching until location data exists.

## Privacy invariants

The following rules are part of the API contract and must not be weakened accidentally:

1. Public job responses never expose latitude or longitude.
2. Public job responses expose only `location_label`.
3. `location_private_label` never appears in public list, job, or nearby-search DTOs.
4. `GET /jobs/{id}/location` requires authentication.
5. The requester can read the exact location of their own job.
6. The assigned worker can read the exact location only while the job is `IN_PROGRESS` or `COMPLETION_REQUESTED`.
7. Unrelated authenticated users cannot read exact job coordinates.
8. A worker loses exact-location access after job completion.

The frontend should clearly label `publicLabel` as public when the user creates a task. Precise addresses or entry instructions belong in `privateLabel`.

## Nearby search

`GET /jobs/nearby` accepts:

- `latitude` in `[-90, 90]`;
- `longitude` in `[-180, 180]`;
- `radiusMeters` from `100` to `50000`;
- `limit` from `1` to `100`.

Matching happens inside PostgreSQL with PostGIS:

- `ST_DWithin` applies the search radius;
- `ST_Distance` returns distance in meters for geography values;
- the GiST index on `jobs.location` supports spatial filtering and nearest-neighbour ordering;
- only jobs in `OPEN` state with non-null location participate.

Application code does not load all jobs and calculate distances in Java.

## Database migration

Flyway migration `V3__job_location_postgis.sql`:

- enables the `postgis` extension;
- adds the geography point and public/private labels;
- adds the GiST spatial index;
- adds an index supporting open-job discovery.

Production database provisioning must allow the migration role to enable PostGIS, or PostGIS must be enabled by infrastructure before Flyway runs.

## Verification

CI verifies the real stack with Docker and PostGIS. The runtime smoke test creates a located job, confirms escrow behaviour, performs a nearby query, checks the returned distance, verifies that public responses contain neither coordinates nor the private label, and confirms that the authenticated exact-location endpoint returns the private data.
