ALTER TABLE monitored_endpoints
    ADD COLUMN check_token UUID,
    ADD COLUMN check_lease_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE monitored_endpoints
    ADD CONSTRAINT ck_endpoint_check_lease
        CHECK (
            (check_token IS NULL AND check_lease_until IS NULL)
                OR
            (check_token IS NOT NULL AND check_lease_until IS NOT NULL)
            );