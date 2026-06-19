package com.churnguard.repository;

import com.churnguard.entity.ChurnPrediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChurnPredictionRepository extends JpaRepository<ChurnPrediction, UUID> {

    List<ChurnPrediction> findByCustomerIdOrderByScoredAtDesc(UUID customerId);

    Optional<ChurnPrediction> findFirstByCustomerIdOrderByScoredAtDesc(UUID customerId);

    // Latest prediction per customer, for all customers of a tenant - powers the at-risk dashboard.
    @Query(value = """
        SELECT cp.* FROM churn_predictions cp
        INNER JOIN (
            SELECT customer_id, MAX(scored_at) AS max_scored_at
            FROM churn_predictions
            GROUP BY customer_id
        ) latest ON cp.customer_id = latest.customer_id AND cp.scored_at = latest.max_scored_at
        INNER JOIN customers c ON c.id = cp.customer_id
        WHERE c.tenant_id = :tenantId
        ORDER BY cp.churn_probability DESC
        """, nativeQuery = true)
    List<ChurnPrediction> findLatestPredictionsForTenant(@Param("tenantId") UUID tenantId);

    @Query(value = """
        SELECT cp.* FROM churn_predictions cp
        INNER JOIN (
            SELECT customer_id, MAX(scored_at) AS max_scored_at
            FROM churn_predictions
            GROUP BY customer_id
        ) latest ON cp.customer_id = latest.customer_id AND cp.scored_at = latest.max_scored_at
        INNER JOIN customers c ON c.id = cp.customer_id
        WHERE c.tenant_id = :tenantId
        ORDER BY cp.churn_probability DESC
        """,
        countQuery = """
        SELECT count(*) FROM churn_predictions cp
        INNER JOIN (
            SELECT customer_id, MAX(scored_at) AS max_scored_at
            FROM churn_predictions
            GROUP BY customer_id
        ) latest ON cp.customer_id = latest.customer_id AND cp.scored_at = latest.max_scored_at
        INNER JOIN customers c ON c.id = cp.customer_id
        WHERE c.tenant_id = :tenantId
        """,
        nativeQuery = true)
    Page<ChurnPrediction> findLatestPredictionsForTenantPaged(@Param("tenantId") UUID tenantId, Pageable pageable);
}