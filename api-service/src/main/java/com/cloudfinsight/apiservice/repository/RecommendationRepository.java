package com.cloudfinsight.apiservice.repository;

import com.cloudfinsight.apiservice.entity.Recommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByStatus(String status);
    List<Recommendation> findByVirtualMachineId(Long virtualMachineId);

    Page<Recommendation> findByVirtualMachineIdOrderByCreatedAtDesc(Long virtualMachineId, Pageable pageable);

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

    @Query("""
        SELECT r FROM Recommendation r
        WHERE r.status = :status
        AND r.id = (
            SELECT MAX(r2.id) FROM Recommendation r2
            WHERE r2.virtualMachine.id = r.virtualMachine.id
            AND r2.status = :status
        )
        AND (:vmId IS NULL OR r.virtualMachine.id = :vmId)
        AND (:type IS NULL OR r.recommendationType = :type)
        """)
    Page<Recommendation> findLatestByStatusAndFilters(
        @Param("status") String status,
        @Param("vmId") Long vmId,
        @Param("type") String type,
        Pageable pageable);

    @Query("""
        SELECT MAX(r.id) FROM Recommendation r
        WHERE r.virtualMachine.id = :vmId
        AND r.status = :status
        """)
    Optional<Long> findLatestIdByVirtualMachineIdAndStatus(@Param("vmId") Long vmId, @Param("status") String status);

    /**
     * Eagerly fetches the VirtualMachine association so the caller can read
     * currentSku/name outside of any @Transactional block, without a lazy-init
     * exception. Used by the /explain endpoint (Task 8.2), which deliberately
     * avoids wrapping the whole cache-check + LLM-call flow in a transaction
     * (would otherwise hold a DB connection open for the duration of an
     * external HTTP call to the LLM provider).
     */
    @Query("SELECT r FROM Recommendation r JOIN FETCH r.virtualMachine WHERE r.id = :id")
    Optional<Recommendation> findByIdWithVirtualMachine(@Param("id") Long id);
}
