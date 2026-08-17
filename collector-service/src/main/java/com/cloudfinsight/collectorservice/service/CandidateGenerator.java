package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.MetricStats;
import com.cloudfinsight.collectorservice.model.UtilisationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CandidateGenerator {

    private static final double MIN_HEADROOM_PERCENT = 20.0;

    private final SkuCatalogueService skuCatalogueService;

    public List<CandidateSku> generateCandidates(String currentArmSkuName, UtilisationSummary summary) {
        Optional<VmSkuCatalogueEntry> currentSkuOpt = skuCatalogueService.findBySkuName(currentArmSkuName);
        if (currentSkuOpt.isEmpty() || !summary.hasData()) {
            return List.of();
        }
        VmSkuCatalogueEntry currentSku = currentSkuOpt.get();

        double cpuP95 = summary.get("percentage_cpu").map(MetricStats::p95).orElse(0.0);
        double memP95 = summary.get("memory_percentage").map(MetricStats::p95).orElse(0.0);

        List<VmSkuCatalogueEntry> pool = skuCatalogueService.findCandidates(
            currentSku.getVcpuCount(), currentSku.getMemoryGb().doubleValue());

        List<CandidateSku> candidates = new ArrayList<>();
        for (VmSkuCatalogueEntry candidate : pool) {
            if (candidate.getArmSkuName().equals(currentSku.getArmSkuName())) {
                continue;
            }

            double cpuHeadroom = headroomPercent(cpuP95, currentSku.getVcpuCount(), candidate.getVcpuCount());
            double memHeadroom = headroomPercent(memP95, currentSku.getMemoryGb().doubleValue(),
                candidate.getMemoryGb().doubleValue());

            if (cpuHeadroom < MIN_HEADROOM_PERCENT || memHeadroom < MIN_HEADROOM_PERCENT) {
                continue;
            }

            String type = classify(currentSku, candidate);
            candidates.add(new CandidateSku(candidate, type, cpuHeadroom, memHeadroom));
        }

        return candidates;
    }

    /**
     * p95UsagePercent is a percentage of the CURRENT VM's own capacity. Scale it to the
     * candidate's capacity first, then express as headroom below 100%.
     */
    private double headroomPercent(double p95UsagePercent, double currentCapacity, double candidateCapacity) {
        if (currentCapacity <= 0 || candidateCapacity <= 0) {
            return 0.0;
        }
        double usageOnCandidate = p95UsagePercent * (currentCapacity / candidateCapacity);
        return 100.0 - usageOnCandidate;
    }

    private String classify(VmSkuCatalogueEntry current, VmSkuCatalogueEntry candidate) {
        boolean candidateOlder = "OLDER_SUPPORTED".equals(candidate.getGeneration());
        boolean currentOlder = "OLDER_SUPPORTED".equals(current.getGeneration());

        if (candidateOlder && !currentOlder) {
            return CandidateSku.CROSS_GENERATION;
        }
        if (candidate.getVcpuCount() < current.getVcpuCount()
            || candidate.getMemoryGb().compareTo(current.getMemoryGb()) < 0) {
            return CandidateSku.DOWNSIZE;
        }
        if (candidate.getVcpuCount() > current.getVcpuCount()
            || candidate.getMemoryGb().compareTo(current.getMemoryGb()) > 0) {
            return CandidateSku.UPSIZE;
        }
        return CandidateSku.CROSS_GENERATION;
    }
}
