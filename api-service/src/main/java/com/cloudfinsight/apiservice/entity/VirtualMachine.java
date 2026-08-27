package com.cloudfinsight.apiservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "virtual_machines")
@Getter
@Setter
public class VirtualMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "azure_resource_id", nullable = false, unique = true)
    private String azureResourceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "resource_group", nullable = false)
    private String resourceGroup;

    @Column(nullable = false)
    private String region;

    @Column(name = "current_sku", nullable = false)
    private String currentSku;

    @Column(name = "os_type", nullable = false)
    private String osType;

    @Column(name = "generation_tag")
    private String generationTag;

    @Column(name = "p95_cpu_percent", precision = 5, scale = 2)
    private BigDecimal p95CpuPercent;

    @Column(name = "p95_mem_percent", precision = 5, scale = 2)
    private BigDecimal p95MemPercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}