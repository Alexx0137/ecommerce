CREATE TABLE IF NOT EXISTS users (
id             BIGSERIAL PRIMARY KEY,
name           VARCHAR(255) NOT NULL,
email          VARCHAR(255) NOT NULL,
password_hash  VARCHAR(255) NOT NULL,
role           VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
deleted_at     TIMESTAMPTZ DEFAULT NULL,

CONSTRAINT valid_role CHECK (role IN ('ADMIN', 'CUSTOMER'))
);

CREATE UNIQUE INDEX users_email_active_idx ON users(email) WHERE deleted_at IS NULL;

COMMENT ON COLUMN users.role IS 'The role of the user: ADMIN or CUSTOMER';