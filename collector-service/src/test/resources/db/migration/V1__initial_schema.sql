CREATE TABLE virtual_machines (
    id                  BIGSERIAL PRIMARY KEY,
    azure_resource_id   TEXT NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    resource_group      VARCHAR(255) NOT NULL,
    region              VARCHAR(100) NOT NULL,
    current_sku         VARCHAR(100) NOT NULL,
    os_type             VARCHAR(50) NOT NULL,
    generation_tag      VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE metric_snapshots (
    id                  BIGSERIAL PRIMARY KEY,
    virtual_machine_id  BIGINT NOT NULL REFERENCES virtual_machines(id) ON DELETE CASCADE,
    metric_name         VARCHAR(50) NOT NULL,   -- e.g. 'cpu_percentage', 'memory_usage_bytes'
    metric_value        DOUBLE PRECISION NOT NULL,
    unit                VARCHAR(20),
    collected_at        TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pricing_snapshots (
    id                  BIGSERIAL PRIMARY KEY,
    arm_sku_name        VARCHAR(100) NOT NULL,
    region              VARCHAR(100) NOT NULL,
    os_type             VARCHAR(50) NOT NULL,
    retail_price        NUMERIC(12, 6) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'USD',
    effective_start_date TIMESTAMPTZ,
    collected_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recommendations (
    id                      BIGSERIAL PRIMARY KEY,
    virtual_machine_id      BIGINT NOT NULL REFERENCES virtual_machines(id) ON DELETE CASCADE,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, APPLIED, DISMISSED
    summary                 TEXT,
    confidence_score        NUMERIC(5, 2),
    estimated_monthly_savings NUMERIC(12, 2),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recommendation_candidates (
    id                      BIGSERIAL PRIMARY KEY,
    recommendation_id       BIGINT NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    candidate_sku           VARCHAR(100) NOT NULL,
    generation_tag          VARCHAR(50),
    estimated_monthly_cost  NUMERIC(12, 2) NOT NULL,
    reliability_score       NUMERIC(5, 2),
    performance_score       NUMERIC(5, 2),
    pros                    TEXT,
    cons                    TEXT,
    is_selected             BOOLEAN NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);