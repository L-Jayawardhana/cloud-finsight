package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.VmSkuCatalogueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VmSkuCatalogueRepository extends JpaRepository<VmSkuCatalogueEntry, Long> {
    Optional<VmSkuCatalogueEntry> findByArmSkuName(String armSkuName);
}
