INSERT INTO vm_sku_catalogue (arm_sku_name, vm_family, generation, vcpu_count, memory_gb, support_status)
VALUES
    ('Standard_B1ms', 'Bms', 'CURRENT', 1, 2, 'SUPPORTED'),
    ('Standard_A1_v2', 'Av2', 'OLDER_SUPPORTED', 1, 2, 'SUPPORTED'),
    ('Standard_D4s_v5', 'Dsv5', 'CURRENT', 4, 16, 'SUPPORTED'),
    ('Standard_D4s_v3', 'Dsv3', 'OLDER_SUPPORTED', 4, 16, 'SUPPORTED');
