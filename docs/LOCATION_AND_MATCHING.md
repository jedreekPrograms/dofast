# Location, routing and live courier tracking

## Purpose

doFast treats location as execution data, not as a public user attribute. A delivery-style task has an origin **A**, a destination **B**, a server-computed route snapshot and, after a worker accepts it, an optional live courier position. Public discovery intentionally receives much less information than the two participants of an active job.

## A → B route model

New jobs are created from a short-lived server route quote instead of trusting distance or ETA values sent by the browser.

The browser selects:

- origin A;
- destination B;
- exact labels/place IDs for execution;
- coarse public labels for discovery.

`POST /routing/quotes` stores both points and obtains a route estimate. A route quote belongs to one user, expires after the configured TTL and can be consumed only once by `POST /jobs`.

The existing `jobs.location` column remains the physical PostGIS origin column to preserve migration safety and nearby-search indexes. V11 adds the destination and route snapshot fields.

Public job DTOs expose only:

- public origin label;
- public destination label;
- route distance;
- route duration.

They never expose exact coordinates, private labels or route execution details.

## Routing providers

Local development and CI use `DETERMINISTIC_DEV`, which makes no external calls and is visibly labelled as a development estimate.

Production should use:

```text
ROUTING_PROVIDER=google
GOOGLE_MAPS_ROUTES_API_KEY=<server restricted key>
```

The Google adapter uses the Routes API for driving estimates and requests the minimum response fields needed by doFast. The server key must never be embedded in browser JavaScript.

The web map uses a separate browser key:

```text
VITE_GOOGLE_MAPS_BROWSER_KEY=<HTTP referrer restricted key>
VITE_GOOGLE_MAPS_MAP_ID=<map id>
```

The browser key is intentionally public and must be restricted by allowed HTTP referrers and API scope. Enable only the APIs required by the map flow (Maps JavaScript API, Places API (New), Geocoding API). Docker supplies these Vite variables as build arguments, because Vite browser variables are compiled at build time.

For local `npm run dev`, provide the same Vite variables in the frontend process environment or `apps/web/.env.local`; the repository root `.env` is primarily the Docker Compose environment.

## Exact route access

`GET /jobs/{id}/route` is authenticated.

- the requester can inspect the exact route for their own job;
- the assigned worker receives exact A/B while the job is active (including dispute evidence rules already used by the job lifecycle);
- unrelated users cannot access it;
- public lists never contain exact A/B.

Nearby matching remains based on origin A and is executed in PostgreSQL/PostGIS with `ST_DWithin`, `ST_Distance` and the GiST index.

## Live courier tracking

Flyway V12 adds a single `job_live_tracking` row per accepted job. doFast intentionally stores the **current state only**, not a GPS trail.

The row can contain:

- current WGS84 position;
- GPS accuracy, heading and speed;
- device capture time and server receive time;
- current phase (`TO_ORIGIN` or `TO_DESTINATION`);
- current remaining distance and ETA;
- the current remaining polyline/provider/computation time.

Tracking lifecycle:

1. worker accepts the job → tracking row starts in `TO_ORIGIN`;
2. worker device sends current GPS while the route page is active;
3. requester receives the current state through `/topic/tracking/{jobId}`;
4. worker confirms pickup at A → phase becomes `TO_DESTINATION`;
5. subsequent GPS updates calculate remaining distance/ETA to B;
6. dispute, completion or cancellation clears precise live location.

The worker is the only user allowed to write GPS updates. The requester and assigned worker may read/subscribe while the job is in an active tracking state. An unrelated user is rejected both by the REST access service and by the STOMP subscription interceptor.

## ETA refresh strategy

GPS and paid road ETA do not have the same cadence.

The web client throttles GPS uploads to roughly five seconds. The API independently enforces `TRACKING_MIN_UPDATE_INTERVAL_MILLIS` (default 1000 ms) against the previous server receive time stored in the locked tracking row. This prevents modified or malicious clients from flooding the position endpoint or multiplying ETA work while keeping the normal five-second browser cadence well above the server floor. The check runs in the same transaction and row lock as the GPS write, so concurrent requests cannot bypass it. Set the value to `0` only in controlled environments that intentionally need the guard disabled.

The backend refreshes the route-provider ETA only when one of these is true:

- there is no current ETA;
- `TRACKING_ETA_REFRESH_SECONDS` elapsed (default 30 s);
- the worker moved by at least `TRACKING_ETA_REFRESH_MOVEMENT_METERS` (default 150 m).

The provider call happens outside the transaction that stores GPS. Its result is written in a second short transaction only if the captured GPS timestamp and route phase are still current. A slow provider therefore cannot overwrite a newer position, and provider failure does not discard the accepted GPS update.

A position older than `TRACKING_STALE_AFTER_SECONDS` (default 20 s) is labelled stale in the UI.

## Position sanity guard

Before replacing the current courier point, the API compares it with the previous accepted device sample. It calculates great-circle distance, subtracts the uncertainty reported by both GPS samples, then divides the effective distance by elapsed capture time. Updates above `TRACKING_MAX_IMPLIED_SPEED_METERS_PER_SECOND` (default 80 m/s, about 288 km/h) are rejected with a conflict response instead of corrupting the map or triggering a misleading ETA refresh.

This is deliberately a coarse integrity guard rather than fraud detection. It tolerates ordinary urban GPS drift through the accuracy allowance, does not retain historical positions, and remains configurable for future transport modes.

## Privacy and retention invariants

1. No public endpoint exposes current courier coordinates.
2. Only the assigned worker may publish location.
3. Only active job participants may read live location or subscribe to its topic.
4. The system does not retain a sequence of historical GPS points.
5. A PostgreSQL trigger clears precise tracking fields whenever a job enters `DISPUTED`, `DONE` or `CANCELLED`, even if application code forgets a cleanup call.
6. Resuming a disputed job never resurrects the old coordinate; a new worker GPS update is required.
7. Out-of-order device updates are rejected so an old coordinate cannot move the courier backwards in time.
8. Obviously implausible position jumps are rejected after accounting for GPS accuracy, protecting map/ETA integrity without building a location history.
9. GPS write cadence is bounded on the server using persisted receive time and a locked tracking row; client-side throttling is never treated as a security boundary.

## Browser and native-app limitation

The current web implementation uses `navigator.geolocation.watchPosition`. It works while the browser page is active, but mobile operating systems may throttle or suspend browser location when the page is backgrounded or the screen is locked.

A true Uber/Bolt-style production mobile experience therefore requires the future native/mobile client to use the operating system's background-location APIs, explicit user permission flow, foreground/background service rules and platform privacy disclosures. The backend protocol created here is already suitable for that client; the transport does not need to be redesigned.

## Verification

The Docker runtime smoke covers the real PostgreSQL/PostGIS stack and verifies:

- A/B route quote ownership and single use;
- no exact-coordinate leakage in public discovery;
- worker exact-route access only after accepting;
- live tracking starts in `TO_ORIGIN`;
- outsider reads and requester GPS writes are forbidden;
- worker GPS produces remaining distance/ETA;
- pickup changes the target to B;
- a second GPS remains in `TO_DESTINATION`;
- opening a dispute clears exact live GPS in PostgreSQL;
- escrow/chat/dispute/refund behaviour still works on the same routed job.

Unit coverage additionally verifies first-sample acceptance, plausible movement, GPS-accuracy allowance, rejection of impossible jumps, and the server-side minimum GPS update interval boundary.