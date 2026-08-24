package com.cloudfinsight.collectorservice.repository;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface VmSkuCatalogueRepository extends JpaRepository<VmSkuCatalogueEntry, Long> {
    List<VmSkuCatalogueEntry> findByVcpuCountBetweenAndMemoryGbBetween(
        Integer vcpuMin, Integer vcpuMax, BigDecimal memoryGbMin, BigDecimal memoryGbMax);

    Optional<VmSkuCatalogueEntry> findByArmSkuName(String armSkuName);
}
