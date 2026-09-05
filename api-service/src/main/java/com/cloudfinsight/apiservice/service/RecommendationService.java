package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.ai.LlmClient;
import com.cloudfinsight.apiservice.ai.RecommendationPromptData;
import com.cloudfinsight.apiservice.dto.ExplanationResponseDto;
import com.cloudfinsight.apiservice.dto.RecommendationCandidateDto;
import com.cloudfinsight.apiservice.dto.RecommendationDetailDto;
import com.cloudfinsight.apiservice.dto.RecommendationHistoryDto;
import com.cloudfinsight.apiservice.dto.RecommendationSummaryDto;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.RecommendationCandidate;
import com.cloudfinsight.apiservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String PENDING_STATUS = "PENDING";
    private static final String EXPLANATION_CACHE_KEY_PREFIX = "explain:";
    private static final Duration EXPLANATION_CACHE_TTL = Duration.ofHours(1);

    private final RecommendationRepository recommendationRepository;
    private final RecommendationCandidateRepository recommendationCandidateRepository;
    private final VirtualMachineRepository virtualMachineRepository;
    private final StringRedisTemplate redisTemplate;
    private final LlmClient llmClient;

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

    @Transactional(readOnly = true)
    public Optional<Page<RecommendationHistoryDto>> getRecommendationHistory(Long vmId, Pageable pageable) {
        if (!virtualMachineRepository.existsById(vmId)) {
            return Optional.empty();
        }

        Long currentId = recommendationRepository
            .findLatestIdByVirtualMachineIdAndStatus(vmId, PENDING_STATUS)
            .orElse(null);

        Page<Recommendation> page = recommendationRepository.findByVirtualMachineIdOrderByCreatedAtDesc(vmId, pageable);
        return Optional.of(page.map(r -> toHistory(r, currentId)));
    }

    /**
     * Deletes a recommendation row outright (hard delete, not a status change).
     * Restricted to the 'admin' role at the SecurityFilterChain level (Task 7.2),
     * added specifically to give Epic 7's 403-insufficient-scope test a real,
     * useful endpoint to gate rather than a placeholder.
     *
     * @return true if a row existed and was deleted, false if no such id existed
     */
    @Transactional
    public boolean deleteRecommendation(Long id) {
        if (!recommendationRepository.existsById(id)) {
            return false;
        }
        recommendationRepository.deleteById(id);
        return true;
    }

    /**
     * Returns an LLM-generated explanation for the given recommendation.
     * Cache-first, deliberately: a Redis hit never touches PostgreSQL at all,
     * and this method is NOT wrapped in a single @Transactional block, since
     * that would hold a DB connection open for the full duration of the
     * outbound LLM HTTP call on every cache miss.
     *
     * @return empty if no recommendation exists with this id (controller maps to 404)
     */
    public Optional<ExplanationResponseDto> explainRecommendation(Long id) {
        String cacheKey = EXPLANATION_CACHE_KEY_PREFIX + id;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Optional.of(new ExplanationResponseDto(cached, true));
        }

        Optional<Recommendation> recommendationOpt = recommendationRepository.findByIdWithVirtualMachine(id);
        if (recommendationOpt.isEmpty()) {
            return Optional.empty();
        }

        RecommendationCandidate selected = recommendationCandidateRepository
            .findByRecommendationIdAndSelectedTrue(id)
            .orElseThrow(() -> new IllegalStateException(
                "Recommendation " + id + " has no selected candidate - data integrity issue"));

        RecommendationPromptData promptData = toPromptData(recommendationOpt.get(), selected);
        String explanation = llmClient.explainRecommendation(promptData);

        redisTemplate.opsForValue().set(cacheKey, explanation, EXPLANATION_CACHE_TTL);
        return Optional.of(new ExplanationResponseDto(explanation, false));
    }

    private RecommendationPromptData toPromptData(Recommendation r, RecommendationCandidate selected) {
        BigDecimal savings = r.getEstimatedMonthlySavings();
        BigDecimal candidateCost = selected.getEstimatedMonthlyCost();
        BigDecimal savingPercent = BigDecimal.ZERO;

        if (savings != null && candidateCost != null) {
            BigDecimal currentCost = savings.add(candidateCost);
            if (currentCost.compareTo(BigDecimal.ZERO) != 0) {
                savingPercent = savings
                    .divide(currentCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            }
        }

        return new RecommendationPromptData(
            r.getVirtualMachine().getName(),
            r.getVirtualMachine().getCurrentSku(),
            selected.getCandidateSku(),
            savings,
            savingPercent,
            r.getConfidenceLevel(),
            splitLines(selected.getPros()),
            splitLines(selected.getCons())
        );
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\n"))
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .toList();
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

    private RecommendationHistoryDto toHistory(Recommendation r, Long currentId) {
        return new RecommendationHistoryDto(
            r.getId(),
            r.getVirtualMachine().getId(),
            r.getVirtualMachine().getName(),
            r.getRecommendationType(),
            r.getConfidenceLevel(),
            r.getEstimatedMonthlySavings(),
            r.getStatus(),
            r.getCreatedAt(),
            currentId != null && currentId.equals(r.getId())
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
