package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {
    List<MetricSnapshot> findByVirtualMachineIdAndCollectedAtBetween(
        Long virtualMachineId, OffsetDateTime start, OffsetDateTime end);
}