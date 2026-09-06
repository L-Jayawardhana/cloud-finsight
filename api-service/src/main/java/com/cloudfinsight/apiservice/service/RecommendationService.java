package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.ai.LlmClient;
import com.cloudfinsight.apiservice.ai.RecommendationPromptData;
import com.cloudfinsight.apiservice.ai.SavingsMath;
import com.cloudfinsight.apiservice.dto.ExplanationResponseDto;
import com.cloudfinsight.apiservice.dto.RecommendationCandidateDto;
import com.cloudfinsight.apiservice.dto.RecommendationDetailDto;
import com.cloudfinsight.apiservice.dto.RecommendationHistoryDto;
import com.cloudfinsight.apiservice.dto.RecommendationSummaryDto;
import com.cloudfinsight.apiservice.entity.MetricSnapshot;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.RecommendationCandidate;
import com.cloudfinsight.apiservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.apiservice.repository.MetricSnapshotRepository;
import com.cloudfinsight.apiservice.repository.PricingSnapshotRepository;
import com.cloudfinsight.apiservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import com.cloudfinsight.apiservice.repository.VmSkuCatalogueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String PENDING_STATUS = "PENDING";
    private static final String EXPLANATION_CACHE_KEY_PREFIX = "explain:";
    private static final Duration EXPLANATION_CACHE_TTL = Duration.ofHours(1);
    private static final BigDecimal HOURS_PER_MONTH = BigDecimal.valueOf(730);
    private static final int UTILISATION_WINDOW_DAYS = 14;

    private final RecommendationRepository recommendationRepository;
    private final RecommendationCandidateRepository recommendationCandidateRepository;
    private final VirtualMachineRepository virtualMachineRepository;
    private final VmSkuCatalogueRepository vmSkuCatalogueRepository;
    private final PricingSnapshotRepository pricingSnapshotRepository;
    private final MetricSnapshotRepository metricSnapshotRepository;
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
        BigDecimal savingPercent = SavingsMath.computeSavingPercent(
            r.getEstimatedMonthlySavings(), selected.getEstimatedMonthlyCost());

        return new RecommendationPromptData(
            r.getVirtualMachine().getName(),
            r.getVirtualMachine().getCurrentSku(),
            selected.getCandidateSku(),
            r.getEstimatedMonthlySavings(),
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
        RecommendationCandidate selected = recommendationCandidateRepository
            .findByRecommendationIdAndSelectedTrue(r.getId())
            .orElse(null);

        return new RecommendationSummaryDto(
            r.getId(),
            r.getVirtualMachine().getId(),
            r.getVirtualMachine().getName(),
            r.getVirtualMachine().getCurrentSku(),
            selected != null ? selected.getCandidateSku() : null,
            selected != null ? selected.getGenerationTag() : null,
            r.getRecommendationType(),
            r.getConfidenceLevel(),
            r.getConfidenceScore(),
            r.getEstimatedMonthlySavings(),
            selected != null
                ? SavingsMath.computeSavingPercent(r.getEstimatedMonthlySavings(), selected.getEstimatedMonthlyCost())
                : BigDecimal.ZERO,
            r.getStatus(),
            r.getCreatedAt(),
            selected != null ? splitLines(selected.getPros()) : List.of(),
            selected != null ? splitLines(selected.getCons()) : List.of()
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
        String currentSku = r.getVirtualMachine().getCurrentSku();
        VmSkuCatalogueEntry currentSkuInfo = vmSkuCatalogueRepository.findByArmSkuName(currentSku).orElse(null);
        BigDecimal currentMonthlyPrice = pricingSnapshotRepository
            .findFirstByArmSkuNameAndRegionOrderByCollectedAtDesc(currentSku, r.getVirtualMachine().getRegion())
            .map(snapshot -> snapshot.getRetailPrice().multiply(HOURS_PER_MONTH))
            .orElse(null);
        Integer dataCoverageDays = computeDataCoverageDays(r.getVirtualMachine().getId());

        List<RecommendationCandidateDto> candidates = recommendationCandidateRepository
            .findByRecommendationId(r.getId())
            .stream()
            .map(c -> toCandidateDto(c, currentMonthlyPrice))
            .toList();

        return new RecommendationDetailDto(
            r.getId(),
            r.getVirtualMachine().getId(),
            r.getVirtualMachine().getName(),
            currentSku,
            currentSkuInfo != null ? currentSkuInfo.getGeneration() : null,
            currentSkuInfo != null ? currentSkuInfo.getVcpuCount() : null,
            currentSkuInfo != null ? currentSkuInfo.getMemoryGb() : null,
            currentMonthlyPrice,
            r.getRecommendationType(),
            r.getConfidenceLevel(),
            r.getConfidenceScore(),
            dataCoverageDays,
            r.getEstimatedMonthlySavings(),
            r.getStatus(),
            r.getSummary(),
            r.getCreatedAt(),
            candidates
        );
    }

    private RecommendationCandidateDto toCandidateDto(RecommendationCandidate c, BigDecimal currentMonthlyPrice) {
        VmSkuCatalogueEntry candidateSkuInfo = vmSkuCatalogueRepository.findByArmSkuName(c.getCandidateSku()).orElse(null);

        Boolean twoInstanceFeasible = null;
        BigDecimal twoInstanceMonthlyCost = null;
        BigDecimal twoInstanceMonthlySaving = null;
        if (currentMonthlyPrice != null && c.getEstimatedMonthlyCost() != null) {
            twoInstanceMonthlyCost = c.getEstimatedMonthlyCost().multiply(BigDecimal.valueOf(2));
            twoInstanceFeasible = twoInstanceMonthlyCost.compareTo(currentMonthlyPrice) <= 0;
            twoInstanceMonthlySaving = currentMonthlyPrice.subtract(twoInstanceMonthlyCost);
        }

        return new RecommendationCandidateDto(
            c.getId(),
            c.getCandidateSku(),
            c.getGenerationTag(),
            candidateSkuInfo != null ? candidateSkuInfo.getVcpuCount() : null,
            candidateSkuInfo != null ? candidateSkuInfo.getMemoryGb() : null,
            c.getEstimatedMonthlyCost(),
            c.getReliabilityScore(),
            c.getPerformanceScore(),
            c.getPros(),
            c.getCons(),
            c.isSelected(),
            twoInstanceFeasible,
            twoInstanceMonthlyCost,
            twoInstanceMonthlySaving
        );
    }

    private Integer computeDataCoverageDays(Long vmId) {
        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusDays(UTILISATION_WINDOW_DAYS);
        List<MetricSnapshot> snapshots = metricSnapshotRepository
            .findByVirtualMachineIdAndCollectedAtBetween(vmId, start, end);
        if (snapshots.isEmpty()) {
            return null;
        }
        OffsetDateTime earliest = snapshots.stream()
            .map(MetricSnapshot::getCollectedAt)
            .min(OffsetDateTime::compareTo)
            .orElseThrow();
        OffsetDateTime latest = snapshots.stream()
            .map(MetricSnapshot::getCollectedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();
        return (int) Math.max(Duration.between(earliest, latest).toDays(), 1);
    }
}
