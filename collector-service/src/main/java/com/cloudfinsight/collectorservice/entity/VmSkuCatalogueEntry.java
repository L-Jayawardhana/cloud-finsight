package com.cloudfinsight.collectorservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vm_sku_catalogue")
@Getter
@Setter
public class VmSkuCatalogueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arm_sku_name", nullable = false, unique = true)
    private String armSkuName;

    @Column(name = "vm_family", nullable = false)
    private String vmFamily;

    @Column(nullable = false)
    private String generation;

    @Column(name = "vcpu_count", nullable = false)
    private Integer vcpuCount;

    @Column(name = "memory_gb", nullable = false)
    private BigDecimal memoryGb;

    @Column(name = "support_status", nullable = false)
    private String supportStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}