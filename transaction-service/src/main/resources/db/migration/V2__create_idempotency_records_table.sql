CREATE TABLE idempotency_records (
    id               BIGSERIAL    PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL UNIQUE,
    reference_number VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_idempotency_records_key ON idempotency_records (idempotency_key);
CREATE INDEX        idx_idempotency_records_ref ON idempotency_records (reference_number);
