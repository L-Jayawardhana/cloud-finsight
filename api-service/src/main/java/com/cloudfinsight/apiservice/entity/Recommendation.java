package com.cloudfinsight.apiservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * api-service never generates its own recommendation ids - every row here mirrors one
 * collector-service already created (RecommendationConsumer assigns the incoming
 * message's id before the first save), so this entity has no @GeneratedValue: that
 * strategy is for ids the JPA provider assigns itself, which conflicts with manually
 * setting one on a not-yet-persisted instance. Implements Persistable so Spring Data
 * decides insert-vs-update from an explicit flag rather than "is the id null" (which
 * would always be false here and wrongly force every first save down the UPDATE path).
 */
@Entity
@Table(name = "recommendations")
@Getter
@Setter
public class Recommendation implements Persistable<Long> {

    @Id
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

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}