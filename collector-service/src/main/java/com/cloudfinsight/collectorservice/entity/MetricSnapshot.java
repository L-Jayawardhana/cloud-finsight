package com.cloudfinsight.collectorservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "metric_snapshots")
@Getter
@Setter
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_machine_id", nullable = false)
    private VirtualMachine virtualMachine;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column
    private String unit;

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}