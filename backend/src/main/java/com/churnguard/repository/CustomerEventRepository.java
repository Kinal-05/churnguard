package com.churnguard.repository;

import com.churnguard.entity.CustomerEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomerEventRepository extends JpaRepository<CustomerEvent, Long> {

    Page<CustomerEvent> findByCustomerIdOrderByOccurredAtDesc(UUID customerId, Pageable pageable);

    @Query("""
        SELECT e FROM CustomerEvent e
        WHERE e.customer.id = :customerId
        AND e.occurredAt >= :since
        ORDER BY e.occurredAt DESC
        """)
    List<CustomerEvent> findRecentByCustomerId(
            @Param("customerId") UUID customerId,
            @Param("since") Instant since
    );

    long countByCustomerIdAndEventTypeAndOccurredAtAfter(
            UUID customerId,
            CustomerEvent.EventType eventType,
            Instant after
    );
}