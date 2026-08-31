# Exact location lifecycle and retention

## Purpose

Exact execution location is sensitive job data. Public discovery exposes only coarse labels; exact coordinates, private address labels and route geometry exist only to prepare and execute a job.

This boundary is independent from live courier tracking. Live tracking already clears the current worker position when tracking stops. This policy defines both when authenticated marketplace participants may read exact persisted job location and when the platform removes that exact execution data from persistence.

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

## Durable retention policy

Terminal API denial is not the final privacy boundary. Flyway `V55__exact_location_retention.sql` adds an auditable `jobs.exact_location_purged_at` marker and permits intermediate-stop geometry to become null after retention cleanup.

The asynchronous retention worker processes only terminal jobs whose terminal timestamp is older than the configured retention period:

- `DONE` uses `completed_at`;
- `CANCELLED` uses `cancelled_at`.

The cleanup deliberately runs outside the money-sensitive completion/cancellation transaction. It selects bounded batches with PostgreSQL `FOR UPDATE SKIP LOCKED`, so multiple API instances can run the scheduler without processing the same terminal job concurrently.

For each due job it removes:

- exact origin geometry;
- origin private address label;
- exact destination geometry;
- destination private address label;
- encoded route polyline;
- intermediate-stop exact geometry;
- intermediate-stop private labels;
- intermediate-stop provider place IDs;
- the consumed route-quote relationship and the now-unreferenced consumed quote, including its exact A/B/stops.

It deliberately preserves:

- job id and lifecycle timestamps;
- requester/worker relationships required by durable marketplace history;
- title/description subject to the separate account/data-retention policy;
- agreed price, expense and escrow/payment accounting records;
- category and assignment history;
- coarse/public origin, destination and stop labels;
- route distance/duration/provider metadata that does not contain exact route geometry.

The cleanup also deletes expired route quotes that are no longer referenced by a job. An abandoned quote is short-lived execution-preparation data and has no reason to retain exact coordinates after it has expired.

## Production configuration

Local development and CI use a 30-day default. Production must make an explicit policy decision through:

- `JOB_EXACT_LOCATION_RETENTION_DAYS` — required by `compose.prod.yaml`, accepted range 1–3650 days;
- `JOB_EXACT_LOCATION_CLEANUP_INTERVAL_MS` — scheduler cadence, default one hour;
- `JOB_EXACT_LOCATION_CLEANUP_BATCH_SIZE` — maximum jobs/quotes processed per transaction, default 100 and accepted range 1–1000.

There is intentionally no hidden production default for the retention period. The correct period depends on the final legal/support/chargeback evidence policy and must be chosen deliberately before deployment.

Changing the retention period affects only when not-yet-purged terminal jobs become eligible. Purged geometry is not reconstructable from public labels and is never resurrected when configuration changes later.

## Financial and dispute boundary

The purge never changes wallet, escrow, payout, refund, platform-revenue or Stripe ledger state. It does not run while a job is `DISPUTED`; exact execution data therefore remains available during the live participant dispute. After a dispute is resolved into `DONE` or `CANCELLED`, the normal terminal retention clock applies.

If future legal or payment-dispute requirements demand another evidence period, deployment configuration must be adjusted before the data becomes due. The application must not copy exact coordinates into financial tables merely to extend retention.

## Verification

Focused service tests verify retention cutoff/batch handling and fail-fast configuration validation.

The real PostgreSQL/PostGIS container smoke additionally completes a multi-stop job, backdates only the CI fixture beyond the one-day CI retention window and verifies that the scheduler:

- writes `exact_location_purged_at`;
- removes exact A/B geometry, private labels and encoded route geometry;
- removes intermediate-stop geometry/private labels/place IDs while retaining public stop labels;
- deletes the consumed route quote and its exact stop records;
- preserves terminal status, price, public labels and route distance;
- leaves terminal participant route access closed.

Production Compose contract validation also fails when the explicit production retention period is omitted.
