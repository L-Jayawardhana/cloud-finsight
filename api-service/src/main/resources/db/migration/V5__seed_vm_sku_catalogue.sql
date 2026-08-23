INSERT INTO vm_sku_catalogue (arm_sku_name, vm_family, generation, vcpu_count, memory_gb, support_status)
VALUES
    ('Standard_D2s_v5', 'Dsv5', 'CURRENT', 2, 8, 'SUPPORTED'),
    ('Standard_D2s_v3', 'Dsv3', 'OLDER_SUPPORTED', 2, 8, 'SUPPORTED'),
    ('Standard_B2s_v2', 'Bv2', 'CURRENT', 2, 8, 'SUPPORTED'),
    ('Standard_B2s', 'B', 'OLDER_SUPPORTED', 2, 4, 'SUPPORTED'),
    ('Standard_E2s_v5', 'Esv5', 'CURRENT', 2, 16, 'SUPPORTED'),
    ('Standard_E2s_v3', 'Esv3', 'OLDER_SUPPORTED', 2, 16, 'SUPPORTED');
