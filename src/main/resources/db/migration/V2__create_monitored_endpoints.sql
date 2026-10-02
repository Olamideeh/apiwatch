CREATE TABLE monitored_endpoints (
                                     id UUID PRIMARY KEY,
                                     owner_id UUID NOT NULL,
                                     name VARCHAR(150) NOT NULL,
                                     url VARCHAR(2048) NOT NULL,
                                     expected_status_code INTEGER NOT NULL DEFAULT 200,
                                     interval_seconds INTEGER NOT NULL DEFAULT 60,
                                     timeout_millis INTEGER NOT NULL DEFAULT 5000,
                                     response_time_limit_millis INTEGER NOT NULL DEFAULT 1000,
                                     paused BOOLEAN NOT NULL DEFAULT FALSE,
                                     current_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                     consecutive_failures INTEGER NOT NULL DEFAULT 0,
                                     outage_open BOOLEAN NOT NULL DEFAULT FALSE,
                                     next_check_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                     last_checked_at TIMESTAMP WITH TIME ZONE,
                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                     updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                     version BIGINT,

                                     CONSTRAINT fk_endpoint_owner
                                         FOREIGN KEY (owner_id) REFERENCES app_users (id),

                                     CONSTRAINT ck_endpoint_expected_status
                                         CHECK (expected_status_code BETWEEN 100 AND 599),

                                     CONSTRAINT ck_endpoint_interval
                                         CHECK (interval_seconds BETWEEN 60 AND 86400),

                                     CONSTRAINT ck_endpoint_timeout
                                         CHECK (timeout_millis BETWEEN 100 AND 30000),

                                     CONSTRAINT ck_endpoint_response_limit
                                         CHECK (
                                             response_time_limit_millis > 0
                                                 AND response_time_limit_millis <= timeout_millis
                                             ),

                                     CONSTRAINT ck_endpoint_failures
                                         CHECK (consecutive_failures >= 0),

                                     CONSTRAINT ck_endpoint_status
                                         CHECK (
                                             current_status IN (
                                                                'PENDING',
                                                                'ONLINE',
                                                                'DEGRADED',
                                                                'OFFLINE'
                                                 )
                                             )
);

CREATE INDEX idx_endpoint_owner
    ON monitored_endpoints (owner_id);

CREATE INDEX idx_endpoint_schedule
    ON monitored_endpoints (paused, next_check_at);