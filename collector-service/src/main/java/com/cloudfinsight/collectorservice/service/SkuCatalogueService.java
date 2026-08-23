package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.repository.VmSkuCatalogueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SkuCatalogueService {

    private static final BigDecimal MEMORY_TOLERANCE_GB = BigDecimal.valueOf(4);

    private final VmSkuCatalogueRepository repository;

    public List<VmSkuCatalogueEntry> findCandidates(int vcpuCount, double memoryGb) {
        BigDecimal target = BigDecimal.valueOf(memoryGb);
        BigDecimal min = target.subtract(MEMORY_TOLERANCE_GB);
        BigDecimal max = target.add(MEMORY_TOLERANCE_GB);

        return repository.findByVcpuCountAndMemoryGbBetween(vcpuCount, min, max);
    }
}