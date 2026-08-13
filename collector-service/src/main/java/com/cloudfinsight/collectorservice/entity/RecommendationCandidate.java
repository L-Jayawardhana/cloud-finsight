package com.cloudfinsight.collectorservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendation_candidates")
@Getter
@Setter
public class RecommendationCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @Column(name = "candidate_sku", nullable = false)
    private String candidateSku;

    @Column(name = "generation_tag")
    private String generationTag;

    @Column(name = "estimated_monthly_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedMonthlyCost;

    @Column(name = "reliability_score", precision = 5, scale = 2)
    private BigDecimal reliabilityScore;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Column(columnDefinition = "TEXT")
    private String pros;

    @Column(columnDefinition = "TEXT")
    private String cons;

    @Column(name = "is_selected", nullable = false)
    private boolean selected = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}