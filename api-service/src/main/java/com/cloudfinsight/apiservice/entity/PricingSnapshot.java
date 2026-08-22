package com.cloudfinsight.apiservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pricing_snapshots")
@Getter
@Setter
public class PricingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arm_sku_name", nullable = false)
    private String armSkuName;

    @Column(nullable = false)
    private String region;

    @Column(name = "os_type", nullable = false)
    private String osType;

    @Column(name = "retail_price", nullable = false, precision = 12, scale = 6)
    private BigDecimal retailPrice;

    @Column(nullable = false)
    private String currency;

    @Column(name = "effective_start_date")
    private OffsetDateTime effectiveStartDate;

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt;

    @PrePersist
    protected void onCreate() {
        if (collectedAt == null) {
            collectedAt = OffsetDateTime.now();
        }
    }
}