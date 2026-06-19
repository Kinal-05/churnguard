package com.churnguard.service;

import com.churnguard.dto.Dtos.EventResponse;
import com.churnguard.dto.Dtos.IngestEventRequest;
import com.churnguard.entity.Customer;
import com.churnguard.entity.CustomerEvent;
import com.churnguard.exception.ResourceNotFoundException;
import com.churnguard.kafka.CustomerEventProducer;
import com.churnguard.repository.CustomerEventRepository;
import com.churnguard.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final CustomerEventRepository eventRepository;
    private final CustomerRepository customerRepository;
    private final CustomerEventProducer eventProducer;

    @Transactional
    public EventResponse ingestEvent(UUID tenantId, IngestEventRequest request) {
        Customer customer = customerRepository
                .findByTenantIdAndExternalRef(tenantId, request.getCustomerExternalRef())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found with externalRef: " + request.getCustomerExternalRef()));

        CustomerEvent event = CustomerEvent.builder()
                .customer(customer)
                .eventType(request.getEventType())
                .eventPayload(request.getEventPayload())
                .occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now())
                .build();

        event = eventRepository.save(event);

        // Publish async to Kafka — ML service will pick this up for feature
        // recomputation. This is fire-and-forget: failure here is logged
        // but does not roll back the event ingestion transaction.
        eventProducer.publishCustomerEvent(
                customer.getId(),
                event.getEventType().name(),
                event.getEventPayload(),
                tenantId.toString()
        );

        log.info("Ingested {} event for customer {} (tenant {})",
                event.getEventType(), customer.getId(), tenantId);

        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getCustomerEvents(UUID tenantId, UUID customerId, int page, int size) {
        // Verify customer belongs to tenant first
        customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        Page<CustomerEvent> events = eventRepository.findByCustomerIdOrderByOccurredAtDesc(
                customerId, PageRequest.of(page, size, Sort.by("occurredAt").descending()));

        return events.getContent().stream().map(this::toResponse).toList();
    }

    private EventResponse toResponse(CustomerEvent event) {
        return EventResponse.builder()
                .id(event.getId())
                .customerId(event.getCustomer().getId())
                .eventType(event.getEventType())
                .eventPayload(event.getEventPayload())
                .occurredAt(event.getOccurredAt())
                .createdAt(event.getCreatedAt())
                .build();
    }
}