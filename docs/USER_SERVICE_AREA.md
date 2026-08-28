# Private worker service area

## Purpose

A worker may optionally define a private center point and radius describing where they want personalized job recommendations. This preference narrows the existing specialization-based `Dla Ciebie` feed without changing public discovery, public profile data, availability or the ability to browse outside the configured area manually.

## Owner API

Authenticated owner-only endpoints:

- `GET /users/me/service-area` — returns the caller's current private preference or `configured=false`;
- `PUT /users/me/service-area` — creates or replaces the center and radius;
- `DELETE /users/me/service-area` — removes the spatial restriction and restores specialization-only recommendations.

The write payload contains only:

- `latitude` in `-90..90`;
- `longitude` in `-180..180`;
- `radiusKm` in `1..100`.

Coordinates must also be finite numbers. The service stores the JTS point as `X=longitude`, `Y=latitude` with SRID 4326.

## Privacy boundary

The exact service-area center is private preference data. It is intentionally absent from:

- `GET /users/{id}/profile`;
- public job discovery;
- job cards and public job detail;
- saved-search shareable URLs;
- chat and notifications;
- trust cards;
- recommendation responses returned to other users.

The owner may read their own coordinates from `/users/me/service-area` so the settings UI can restore the marker. The personalized recommendation response exposes only `serviceAreaRadiusKm`, allowing the owner UI to explain that area filtering is active without copying the private center into the discovery URL.

The free-form public profile location remains independent. A user may publicly write `Wrocław i okolice` while keeping the actual recommendation center private.

## Recommendation semantics

Without a configured service area, `GET /jobs/recommended` keeps the existing specialization-only behavior.

With a configured service area, recommended jobs must additionally:

- have a persisted job location;
- have their origin/location point within the configured radius according to PostGIS `ST_DWithin`;
- still match one of the caller's active concrete specializations;
- still be `OPEN`;
- still belong to another user;
- still have no bilateral user-block relationship.

All filtering happens in PostgreSQL before pagination. Exact job coordinates remain excluded from `JobResponse`; configuring a service area does not grant any additional location access.

For point-to-point work, matching uses the job's origin location. It does not require the destination to be inside the worker's service area because the preference describes where the worker is willing to start jobs, not a hard geofence for the whole route.

## Persistence

Flyway `V34__user_service_area.sql` creates `user_service_areas` with:

- one row per user enforced by a unique `user_id`;
- `geography(Point,4326)` center;
- radius constraint `1000..100000` meters;
- `ON DELETE CASCADE` user ownership;
- GIST index on the center geography.

The database constraint duplicates the application-level 1–100 km validation so malformed custom clients cannot persist an arbitrary radius.

## Web UX

Profile settings contain an `Obszar działania` panel using the existing map/location picker plus a radius selector. Users can create, update or disable the preference. The UI explicitly states that the exact point is private and is used only for personalized matching.

If Google Maps is not configured, the existing manual location fallback remains available for local development.

## Verification

Backend unit coverage verifies:

- creation and update preserve JTS longitude/latitude orientation;
- reads return owner coordinates only through the owner service;
- deletion is scoped to the authenticated user;
- no-specialization recommendation requests still avoid a jobs query;
- recommendation behavior falls back to category-only matching when no service area exists;
- configured areas route to the PostGIS recommendation query with the correct latitude, longitude and radius.

CI must also pass Maven verify, frontend audit/lint/build and the PostgreSQL/PostGIS runtime smoke before merge.
