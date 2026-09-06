CREATE TABLE vm_sku_catalogue (
    id              BIGSERIAL PRIMARY KEY,
    arm_sku_name    VARCHAR(100) NOT NULL UNIQUE,
    vm_family       VARCHAR(50) NOT NULL,
    generation      VARCHAR(30) NOT NULL,
    vcpu_count      INTEGER NOT NULL,
    memory_gb       NUMERIC(6, 2) NOT NULL,
    support_status  VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vm_sku_catalogue_vcpu_memory
    ON vm_sku_catalogue (vcpu_count, memory_gb);
