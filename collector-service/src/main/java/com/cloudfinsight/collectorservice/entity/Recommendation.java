package com.cloudfinsight.collectorservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_machine_id", nullable = false)
    private VirtualMachine virtualMachine;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "recommendation_type", nullable = false)
    private String recommendationType = "DOWNSIZE";

    @Column(name = "confidence_level")
    private String confidenceLevel;

    @Column
    private String summary;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "estimated_monthly_savings", precision = 12, scale = 2)
    private BigDecimal estimatedMonthlySavings;

    @OneToMany(mappedBy = "recommendation", fetch = FetchType.LAZY)
    private List<RecommendationCandidate> candidates = new ArrayList<>();

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