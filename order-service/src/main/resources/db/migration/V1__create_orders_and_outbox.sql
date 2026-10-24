CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     VARCHAR(64)    NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    idempotency_key VARCHAR(100)   UNIQUE,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);

CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts     INT          NOT NULL DEFAULT 0
);

-- The publisher looks for unpublished rows, oldest first.
CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, created_at);
