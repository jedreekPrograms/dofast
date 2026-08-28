ALTER TABLE users
    ADD COLUMN bio VARCHAR(600),
    ADD COLUMN public_location VARCHAR(120);

ALTER TABLE users
    ADD CONSTRAINT chk_users_bio_not_blank
        CHECK (bio IS NULL OR length(btrim(bio)) BETWEEN 1 AND 600),
    ADD CONSTRAINT chk_users_public_location_not_blank
        CHECK (public_location IS NULL OR length(btrim(public_location)) BETWEEN 1 AND 120);
