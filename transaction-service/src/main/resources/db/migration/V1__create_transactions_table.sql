CREATE TABLE transactions (
    id               BIGSERIAL        PRIMARY KEY,
    reference_number VARCHAR(20)      NOT NULL UNIQUE,
    source_account   VARCHAR(50)      NOT NULL,
    target_account   VARCHAR(50)      NOT NULL,
    amount           NUMERIC(19, 4)   NOT NULL,
    currency         VARCHAR(3)       NOT NULL DEFAULT 'USD',
    status           VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
    transaction_type VARCHAR(20)      NOT NULL,
    description      VARCHAR(500),
    failure_reason   VARCHAR(1000),
    idempotency_key  VARCHAR(255),
    created_at       TIMESTAMP        NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP
);

CREATE UNIQUE INDEX idx_transactions_reference_number ON transactions (reference_number);
CREATE INDEX        idx_transactions_source_account   ON transactions (source_account);
CREATE INDEX        idx_transactions_target_account   ON transactions (target_account);
CREATE INDEX        idx_transactions_status           ON transactions (status);
CREATE INDEX        idx_transactions_idempotency_key  ON transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
