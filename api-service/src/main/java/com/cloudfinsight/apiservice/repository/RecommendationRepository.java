package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByStatus(String status);
    List<Recommendation> findByVirtualMachineId(Long virtualMachineId);

    @Query("""
        SELECT r FROM Recommendation r
        WHERE r.status = :status
        AND r.id = (
            SELECT MAX(r2.id) FROM Recommendation r2
            WHERE r2.virtualMachine.id = r.virtualMachine.id
            AND r2.status = :status
        )
        """)
    List<Recommendation> findLatestByStatus(@Param("status") String status);
}
