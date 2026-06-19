package com.churnguard.repository;

import com.churnguard.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndExternalRef(UUID tenantId, String externalRef);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenant.id = :tenantId
        AND c.status = :status
        """)
    Page<Customer> findByTenantIdAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") Customer.CustomerStatus status,
            Pageable pageable
    );

    long countByTenantIdAndStatus(UUID tenantId, Customer.CustomerStatus status);
}