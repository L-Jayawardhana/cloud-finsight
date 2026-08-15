package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByStatus(String status);
    List<Recommendation> findByVirtualMachineId(Long virtualMachineId);
}