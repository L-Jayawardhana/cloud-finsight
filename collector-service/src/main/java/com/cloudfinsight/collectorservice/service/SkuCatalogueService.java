package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.repository.VmSkuCatalogueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SkuCatalogueService {

    private static final BigDecimal MEMORY_TOLERANCE_GB = BigDecimal.valueOf(4);
    private static final int VCPU_TOLERANCE = 2;

    private final VmSkuCatalogueRepository repository;

    public List<VmSkuCatalogueEntry> findCandidates(int vcpuCount, double memoryGb) {
        BigDecimal targetMemory = BigDecimal.valueOf(memoryGb);
        BigDecimal minMemory = targetMemory.subtract(MEMORY_TOLERANCE_GB);
        BigDecimal maxMemory = targetMemory.add(MEMORY_TOLERANCE_GB);

        int minVcpu = Math.max(1, vcpuCount - VCPU_TOLERANCE);
        int maxVcpu = vcpuCount + VCPU_TOLERANCE;

        return repository.findByVcpuCountBetweenAndMemoryGbBetween(minVcpu, maxVcpu, minMemory, maxMemory);
    }

    public List<String> getAllArmSkuNames() {
        return repository.findAll().stream()
            .map(VmSkuCatalogueEntry::getArmSkuName)
            .toList();
    }

    public Optional<VmSkuCatalogueEntry> findBySkuName(String armSkuName) {
        return repository.findByArmSkuName(armSkuName);
    }
}
