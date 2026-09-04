package com.cloudfinsight.collectorservice.repository;

import com.cloudfinsight.collectorservice.entity.RecommendationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationCandidateRepository extends JpaRepository<RecommendationCandidate, Long> {
    List<RecommendationCandidate> findByRecommendationId(Long recommendationId);

    Optional<RecommendationCandidate> findByRecommendationIdAndSelectedTrue(Long recommendationId);
}