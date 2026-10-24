CREATE TABLE payments (
    id             UUID PRIMARY KEY,
    order_id       UUID           NOT NULL UNIQUE,
    amount         NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Idempotent consumer: one row per Kafka event already handled.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
