ALTER TABLE virtual_machines
    ADD COLUMN p95_cpu_percent NUMERIC(5,2),
    ADD COLUMN p95_mem_percent NUMERIC(5,2);
