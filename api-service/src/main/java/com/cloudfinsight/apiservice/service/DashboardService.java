package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.dto.CostSummaryDto;
import com.cloudfinsight.apiservice.dto.VmSummaryDto;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.VirtualMachine;
import com.cloudfinsight.apiservice.repository.PricingSnapshotRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal HOURS_PER_MONTH = BigDecimal.valueOf(730);
    private static final String PENDING_STATUS = "PENDING";

    private final VirtualMachineRepository virtualMachineRepository;
    private final PricingSnapshotRepository pricingSnapshotRepository;
    private final RecommendationRepository recommendationRepository;

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