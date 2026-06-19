CREATE TABLE churn_predictions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id             UUID NOT NULL REFERENCES customers(id),
    churn_probability       DOUBLE PRECISION NOT NULL,
    risk_tier               VARCHAR(20) NOT NULL,
    revenue_at_risk_cents   BIGINT NOT NULL,
    model_version           VARCHAR(50) NOT NULL,
    explanation             JSONB NOT NULL,
    scored_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_churn_predictions_customer_time ON churn_predictions(customer_id, scored_at DESC);
CREATE INDEX idx_churn_predictions_risk_tier ON churn_predictions(risk_tier);

CREATE TABLE model_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version         VARCHAR(50) NOT NULL UNIQUE,
    trained_at      TIMESTAMPTZ NOT NULL,
    auc_score       DOUBLE PRECISION,
    precision_score DOUBLE PRECISION,
    recall_score    DOUBLE PRECISION,
    is_active       BOOLEAN NOT NULL DEFAULT false,
    notes           TEXT
);