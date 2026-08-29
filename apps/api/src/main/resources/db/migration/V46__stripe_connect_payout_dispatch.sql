ALTER TABLE payout_requests
    ADD COLUMN provider_transfer_reference VARCHAR(255);

ALTER TABLE payout_requests
    ADD CONSTRAINT chk_payout_requests_transfer_reference CHECK (
        provider_transfer_reference IS NULL OR char_length(trim(provider_transfer_reference)) > 0
    );

CREATE UNIQUE INDEX uk_payout_requests_provider_transfer_reference
    ON payout_requests (provider_code, provider_transfer_reference)
    WHERE provider_transfer_reference IS NOT NULL;
