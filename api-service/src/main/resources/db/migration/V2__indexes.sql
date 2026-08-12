CREATE INDEX idx_metric_snapshots_vm_id_collected_at
    ON metric_snapshots (virtual_machine_id, collected_at DESC);

CREATE INDEX idx_pricing_snapshots_sku_region
    ON pricing_snapshots (arm_sku_name, region);

CREATE INDEX idx_recommendations_vm_id
    ON recommendations (virtual_machine_id);

CREATE INDEX idx_recommendations_status
    ON recommendations (status);

CREATE INDEX idx_recommendation_candidates_recommendation_id
    ON recommendation_candidates (recommendation_id);