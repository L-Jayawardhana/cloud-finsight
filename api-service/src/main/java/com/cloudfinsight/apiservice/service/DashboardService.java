package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.dto.CostSummaryDto;
import com.cloudfinsight.apiservice.dto.UtilisationPointDto;
import com.cloudfinsight.apiservice.dto.VmSummaryDto;
import com.cloudfinsight.apiservice.entity.MetricSnapshot;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.VirtualMachine;
import com.cloudfinsight.apiservice.repository.MetricSnapshotRepository;
import com.cloudfinsight.apiservice.repository.PricingSnapshotRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal HOURS_PER_MONTH = BigDecimal.valueOf(730);
    private static final String PENDING_STATUS = "PENDING";
    private static final String CPU_METRIC = "percentage_cpu";
    private static final String MEMORY_METRIC = "memory_percentage";

    private final VirtualMachineRepository virtualMachineRepository;
    private final PricingSnapshotRepository pricingSnapshotRepository;
    private final RecommendationRepository recommendationRepository;
    private final MetricSnapshotRepository metricSnapshotRepository;

    @Cacheable("vm-summaries")
    public List<VmSummaryDto> getVmSummaries() {
        return virtualMachineRepository.findAll().stream()
            .map(this::toSummary)
            .toList();
    }

    @Cacheable("cost-summary")
    public CostSummaryDto getCostSummary() {
        List<VmSummaryDto> summaries = virtualMachineRepository.findAll().stream()
            .map(this::toSummary)
            .toList();

        BigDecimal totalMonthlySpend = summaries.stream()
            .map(VmSummaryDto::currentMonthlyPrice)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Recommendation> pending = recommendationRepository.findLatestByStatus(PENDING_STATUS);
        BigDecimal totalPotentialSaving = pending.stream()
            .map(Recommendation::getEstimatedMonthlySavings)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CostSummaryDto(totalMonthlySpend, totalPotentialSaving, summaries.size(), pending.size());
    }

    /**
     * Computed at read time from raw metric_snapshots rather than persisted rollups -
     * fine for this project's data volume, and avoids a migration + collector-service
     * change to add daily aggregate storage.
     */
    @Transactional(readOnly = true)
    public Optional<List<UtilisationPointDto>> getUtilisation(Long vmId, int days) {
        if (!virtualMachineRepository.existsById(vmId)) {
            return Optional.empty();
        }

        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusDays(days);
        List<MetricSnapshot> snapshots = metricSnapshotRepository
            .findByVirtualMachineIdAndCollectedAtBetween(vmId, start, end);

        Map<LocalDate, List<MetricSnapshot>> byDay = snapshots.stream()
            .collect(Collectors.groupingBy(s -> s.getCollectedAt().toLocalDate()));

        List<UtilisationPointDto> points = byDay.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> toUtilisationPoint(entry.getKey(), entry.getValue()))
            .toList();

        return Optional.of(points);
    }

    private UtilisationPointDto toUtilisationPoint(LocalDate date, List<MetricSnapshot> daySnapshots) {
        List<Double> cpuValues = sortedValuesForMetric(daySnapshots, CPU_METRIC);
        List<Double> memValues = sortedValuesForMetric(daySnapshots, MEMORY_METRIC);

        return new UtilisationPointDto(
            date,
            percentile(cpuValues, 50),
            percentile(cpuValues, 95),
            max(cpuValues),
            percentile(memValues, 50),
            percentile(memValues, 95),
            max(memValues)
        );
    }

    private List<Double> sortedValuesForMetric(List<MetricSnapshot> snapshots, String metricName) {
        return snapshots.stream()
            .filter(s -> metricName.equals(s.getMetricName()))
            .map(MetricSnapshot::getMetricValue)
            .sorted()
            .toList();
    }

    private BigDecimal percentile(List<Double> sortedValues, double percentileRank) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        if (sortedValues.size() == 1) {
            return round(sortedValues.get(0));
        }
        double rank = (percentileRank / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        double weight = rank - lowerIndex;
        double value = sortedValues.get(lowerIndex) + weight * (sortedValues.get(upperIndex) - sortedValues.get(lowerIndex));
        return round(value);
    }

    private BigDecimal max(List<Double> sortedValues) {
        return sortedValues.isEmpty() ? null : round(sortedValues.get(sortedValues.size() - 1));
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private VmSummaryDto toSummary(VirtualMachine vm) {
        BigDecimal currentMonthlyPrice = pricingSnapshotRepository
            .findFirstByArmSkuNameAndRegionOrderByCollectedAtDesc(vm.getCurrentSku(), vm.getRegion())
            .map(snapshot -> snapshot.getRetailPrice().multiply(HOURS_PER_MONTH))
            .orElse(null);

        return new VmSummaryDto(
            vm.getId(),
            vm.getName(),
            vm.getCurrentSku(),
            vm.getRegion(),
            currentMonthlyPrice,
            vm.getP95CpuPercent(),
            vm.getP95MemPercent()
        );
    }
}
