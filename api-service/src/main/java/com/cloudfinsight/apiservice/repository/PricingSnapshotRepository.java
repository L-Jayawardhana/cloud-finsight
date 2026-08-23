package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.PricingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingSnapshotRepository extends JpaRepository<PricingSnapshot, Long> {
    List<PricingSnapshot> findByArmSkuNameAndRegion(String armSkuName, String region);
}