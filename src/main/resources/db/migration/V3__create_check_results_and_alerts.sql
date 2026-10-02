CREATE TABLE check_results (
                               id UUID PRIMARY KEY,
                               endpoint_id UUID NOT NULL,
                               status VARCHAR(20) NOT NULL,
                               actual_status_code INTEGER,
                               response_time_millis BIGINT NOT NULL,
                               error_message VARCHAR(2000),
                               checked_at TIMESTAMP WITH TIME ZONE NOT NULL,

                               CONSTRAINT fk_check_result_endpoint
                                   FOREIGN KEY (endpoint_id) REFERENCES monitored_endpoints (id),

                               CONSTRAINT ck_check_result_status
                                   CHECK (status IN ('ONLINE', 'DEGRADED', 'OFFLINE')),

                               CONSTRAINT ck_check_result_http_status
                                   CHECK (
                                       actual_status_code IS NULL
                                           OR actual_status_code BETWEEN 100 AND 599
                                       ),

                               CONSTRAINT ck_check_result_duration
                                   CHECK (response_time_millis >= 0)
);

CREATE INDEX idx_check_result_endpoint_time
    ON check_results (endpoint_id, checked_at);

CREATE TABLE alerts (
                        id UUID PRIMARY KEY,
                        endpoint_id UUID NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        recipient_email VARCHAR(200) NOT NULL,
                        delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        last_error VARCHAR(2000),
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        sent_at TIMESTAMP WITH TIME ZONE,
                        version BIGINT,

                        CONSTRAINT fk_alert_endpoint
                            FOREIGN KEY (endpoint_id) REFERENCES monitored_endpoints (id),

                        CONSTRAINT ck_alert_type
                            CHECK (type IN ('OUTAGE', 'RECOVERY')),

                        CONSTRAINT ck_alert_delivery_status
                            CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),

                        CONSTRAINT ck_alert_attempt_count
                            CHECK (attempt_count >= 0),

                        CONSTRAINT ck_alert_sent_at
                            CHECK (delivery_status <> 'SENT' OR sent_at IS NOT NULL)
);

CREATE INDEX idx_alert_endpoint_time
    ON alerts (endpoint_id, created_at);

CREATE INDEX idx_alert_delivery_status
    ON alerts (delivery_status);