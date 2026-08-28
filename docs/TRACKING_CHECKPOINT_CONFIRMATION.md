# Tracking checkpoint confirmation

Route checkpoints are participant-only execution events. A worker cannot advance tracking from origin A or an intermediate stop merely by pressing the confirmation button.

Before `POST /jobs/{jobId}/tracking/checkpoint` or the legacy pickup alias can advance the route, the API requires:

- an active job whose assigned worker matches the authenticated account;
- an initialized live-tracking row;
- a current GPS position received within `TRACKING_STALE_AFTER_SECONDS`;
- the worker position to be within `TRACKING_CHECKPOINT_ARRIVAL_RADIUS_METERS` of the current target after allowing for the device-reported GPS accuracy.

The default arrival radius is 100 metres. GPS accuracy is treated as uncertainty, not as extra travel distance: the validator subtracts the reported accuracy from the measured great-circle distance and compares the remaining effective distance with the configured radius.

This prevents accidental or malicious phase advancement from a remote location while still tolerating normal mobile GPS uncertainty. It also means a stale marker cannot be reused to confirm a checkpoint after the worker has moved away.

The guard does not store additional location history and therefore preserves the current privacy model: only the latest live position remains in `job_live_tracking` and precise coordinates are cleared by the existing lifecycle cleanup rules.
