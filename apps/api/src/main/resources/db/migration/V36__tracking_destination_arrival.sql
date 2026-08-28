ALTER TABLE job_live_tracking
    DROP CONSTRAINT chk_live_tracking_phase,
    ADD CONSTRAINT chk_live_tracking_phase
        CHECK (phase IN ('TO_ORIGIN', 'TO_STOP', 'TO_DESTINATION', 'ARRIVED_DESTINATION'));
