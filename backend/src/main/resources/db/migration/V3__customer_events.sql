CREATE TABLE customer_events (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     UUID NOT NULL REFERENCES customers(id),
    event_type      VARCHAR(100) NOT NULL,
    event_payload   JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_events_customer_time ON customer_events(customer_id, occurred_at DESC);
CREATE INDEX idx_customer_events_type ON customer_events(event_type);