ALTER TABLE admin_user_reactivation_audits
    ADD COLUMN reason VARCHAR(1000);

UPDATE admin_user_reactivation_audits
SET reason = 'Powód nie był rejestrowany przed migracją V29'
WHERE reason IS NULL;

ALTER TABLE admin_user_reactivation_audits
    ALTER COLUMN reason SET NOT NULL;

ALTER TABLE admin_user_reactivation_audits
    ADD CONSTRAINT ck_admin_user_reactivation_reason_not_blank
        CHECK (length(trim(reason)) > 0);
