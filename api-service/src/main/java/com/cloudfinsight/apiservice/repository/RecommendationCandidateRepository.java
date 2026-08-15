package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.RecommendationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationCandidateRepository extends JpaRepository<RecommendationCandidate, Long> {
    List<RecommendationCandidate> findByRecommendationId(Long recommendationId);
}