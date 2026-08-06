package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.dto.RecommendationCandidateDto;
import com.cloudfinsight.apiservice.dto.RecommendationDetailDto;
import com.cloudfinsight.apiservice.dto.RecommendationSummaryDto;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String PENDING_STATUS = "PENDING";

    private final RecommendationRepository recommendationRepository;
    private final RecommendationCandidateRepository recommendationCandidateRepository;

    @Transactional(readOnly = true)
    public Page<RecommendationSummaryDto> listRecommendations(Long vmId, String type, Pageable pageable) {
        return recommendationRepository
            .findLatestByStatusAndFilters(PENDING_STATUS, vmId, type, pageable)
            .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Optional<RecommendationDetailDto> getRecommendationDetail(Long id) {
        return recommendationRepository.findById(id).map(this::toDetail);
    }

    private RecommendationSummaryDto toSummary(Recommendation r) {
        return new RecommendationSummaryDto(
            r.getId(),
            r.getVirtualMachine().getId(),
            r.getVirtualMachine().getName(),
            r.getRecommendationType(),
            r.getConfidenceLevel(),
            r.getEstimatedMonthlySavings(),
            r.getStatus(),
            r.getCreatedAt()
        );
    }

    private RecommendationDetailDto toDetail(Recommendation r) {
        List<RecommendationCandidateDto> candidates = recommendationCandidateRepository
            .findByRecommendationId(r.getId())
            .stream()
            .map(c -> new RecommendationCandidateDto(
                c.getId(),
                c.getCandidateSku(),
                c.getGenerationTag(),
                c.getEstimatedMonthlyCost(),
                c.getReliabilityScore(),
                c.getPerformanceScore(),
                c.getPros(),
                c.getCons(),
                c.isSelected()
            ))
            .toList();

        return new RecommendationDetailDto(
            r.getId(),
            r.getVirtualMachine().getId(),
            r.getVirtualMachine().getName(),
            r.getRecommendationType(),
            r.getConfidenceLevel(),
            r.getConfidenceScore(),
            r.getEstimatedMonthlySavings(),
            r.getStatus(),
            r.getSummary(),
            r.getCreatedAt(),
            candidates
        );
    }
}
