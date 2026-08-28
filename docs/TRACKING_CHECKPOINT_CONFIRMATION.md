# Tracking checkpoint confirmation

Route checkpoints are participant-only execution events. A worker cannot advance tracking from origin A, an intermediate stop, or destination B merely by pressing the confirmation button.

Before `POST /jobs/{jobId}/tracking/checkpoint` or the legacy pickup alias can advance the route, the API requires:

- an active job whose assigned worker matches the authenticated account;
- an initialized live-tracking row;
- a current GPS position received within `TRACKING_STALE_AFTER_SECONDS`;
- the worker position to be within `TRACKING_CHECKPOINT_ARRIVAL_RADIUS_METERS` of the current target after allowing for the device-reported GPS accuracy.

The default arrival radius is 100 metres. GPS accuracy is treated as uncertainty, not as extra travel distance: the validator subtracts the reported accuracy from the measured great-circle distance and compares the remaining effective distance with the configured radius.

When the current phase is `TO_DESTINATION`, a successful checkpoint confirmation moves tracking to `ARRIVED_DESTINATION`. This is intentionally separate from the job-completion lifecycle: arriving at B does not by itself mark the job as completed or release settlement. It only records that the tracked route has reached its final target.

Final arrival also stops live location sharing immediately. The latest precise GPS point and route estimate are cleared, and subsequent location updates are rejected while tracking remains in `ARRIVED_DESTINATION`. This minimizes collection of worker location after the route no longer needs live navigation.

## Web behavior

The route page exposes the same checkpoint action for A, every intermediate stop and B. In `TO_DESTINATION` the worker sees an explicit **Potwierdź dotarcie do punktu B** action; the backend remains authoritative and rejects the transition unless the current GPS sample satisfies the freshness and proximity policy above.

After the server returns `ARRIVED_DESTINATION`, the web client immediately tears down its browser geolocation watcher. The realtime courier marker and remaining ETA disappear because the backend has cleared precise tracking data. The page shows a completed-route state instead of incorrectly claiming that it is waiting for the first GPS fix.

The final-arrival screen explicitly directs the worker back to the job details for the separate **Zgłoś wykonanie** lifecycle action. Route arrival therefore cannot be mistaken for completion confirmation or payment release.

This prevents accidental or malicious phase advancement from a remote location while still tolerating normal mobile GPS uncertainty. It also means a stale marker cannot be reused to confirm a checkpoint after the worker has moved away.

The guard does not store location history: only the latest live position remains in `job_live_tracking` while a route is active, and precise coordinates are cleared on final arrival or by the existing lifecycle cleanup rules.
