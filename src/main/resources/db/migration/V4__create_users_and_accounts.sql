CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    identifier VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT uk_app_users_identifier UNIQUE (identifier)
);

CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_accounts_user UNIQUE (user_id),
    CONSTRAINT uk_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_app_users_email ON app_users (email);
