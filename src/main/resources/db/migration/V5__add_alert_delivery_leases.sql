ALTER TABLE alerts
    ADD COLUMN delivery_token UUID,
    ADD COLUMN delivery_lease_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE alerts
    ADD CONSTRAINT ck_alert_delivery_lease
        CHECK (
            (delivery_token IS NULL AND delivery_lease_until IS NULL)
                OR
            (delivery_token IS NOT NULL AND delivery_lease_until IS NOT NULL)
            );