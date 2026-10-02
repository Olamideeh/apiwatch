CREATE TABLE app_users (
                           id UUID PRIMARY KEY,
                           full_name VARCHAR(150) NOT NULL,
                           email VARCHAR(200) NOT NULL,
                           password_hash VARCHAR(255) NOT NULL,
                           active BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                           version BIGINT,
                           CONSTRAINT uk_app_user_email UNIQUE (email)
);