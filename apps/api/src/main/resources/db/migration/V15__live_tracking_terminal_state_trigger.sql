CREATE OR REPLACE FUNCTION clear_live_tracking_when_job_pauses_or_closes()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('DISPUTED', 'DONE', 'CANCELLED')
       AND OLD.status IS DISTINCT FROM NEW.status THEN
        UPDATE job_live_tracking
        SET current_location = NULL,
            accuracy_meters = NULL,
            heading_degrees = NULL,
            speed_meters_per_second = NULL,
            captured_at = NULL,
            received_at = CURRENT_TIMESTAMP,
            sharing_stopped_at = CURRENT_TIMESTAMP,
            remaining_distance_meters = NULL,
            remaining_duration_seconds = NULL,
            remaining_encoded_polyline = NULL,
            remaining_provider = NULL,
            remaining_computed_at = NULL,
            eta_origin_location = NULL
        WHERE job_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
