package com.cloudfinsight.collectorservice.repository;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface VmSkuCatalogueRepository extends JpaRepository<VmSkuCatalogueEntry, Long> {
    List<VmSkuCatalogueEntry> findByVcpuCountAndMemoryGbBetween(
        Integer vcpuCount, BigDecimal memoryGbMin, BigDecimal memoryGbMax);
}