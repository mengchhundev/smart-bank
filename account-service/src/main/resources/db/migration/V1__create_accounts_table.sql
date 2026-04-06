CREATE TABLE accounts (
    id                  BIGSERIAL       PRIMARY KEY,
    account_number      VARCHAR(20)     NOT NULL UNIQUE,
    account_holder_name VARCHAR(100)    NOT NULL,
    account_type        VARCHAR(20)     NOT NULL,
    balance             NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    user_id             BIGINT          NOT NULL,
    version             INTEGER         NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);
CREATE INDEX idx_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_status ON accounts (status);
