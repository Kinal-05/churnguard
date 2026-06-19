package com.churnguard.service;

import com.churnguard.dto.Dtos.*;
import com.churnguard.entity.ChurnPrediction;
import com.churnguard.entity.Customer;
import com.churnguard.entity.Tenant;
import com.churnguard.exception.ResourceNotFoundException;
import com.churnguard.repository.ChurnPredictionRepository;
import com.churnguard.repository.CustomerRepository;
import com.churnguard.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ChurnPredictionRepository predictionRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public CustomerResponse upsertCustomer(UUID tenantId, UpsertCustomerRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        // Upsert: update if externalRef already exists for this tenant, else create
        Customer customer = customerRepository
                .findByTenantIdAndExternalRef(tenantId, request.getExternalRef())
                .orElse(Customer.builder()
                        .tenant(tenant)
                        .externalRef(request.getExternalRef())
                        .build());

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPlan(request.getPlan());
        customer.setMrrCents(request.getMrrCents());
        customer.setSignupDate(request.getSignupDate());
        if (request.getStatus() != null) {
            customer.setStatus(request.getStatus());
        }

        customer = customerRepository.save(customer);
        return toResponse(customer, null);
    }

    @Transactional(readOnly = true)
    public PagedCustomerResponse listCustomers(UUID tenantId, int page, int size) {
        Page<Customer> customerPage = customerRepository.findByTenantId(
                tenantId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        List<CustomerResponse> content = customerPage.getContent().stream()
                .map(c -> {
                    ChurnPrediction latest = predictionRepository
                            .findFirstByCustomerIdOrderByScoredAtDesc(c.getId())
                            .orElse(null);
                    return toResponse(c, latest);
                })
                .toList();

        return PagedCustomerResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(customerPage.getTotalElements())
                .totalPages(customerPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID tenantId, UUID customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        ChurnPrediction latest = predictionRepository
                .findFirstByCustomerIdOrderByScoredAtDesc(customerId)
                .orElse(null);

        return toResponse(customer, latest);
    }

    // ── Mapping helpers ────────────────────────────────────────────────────────

    public CustomerResponse toResponse(Customer customer, ChurnPrediction prediction) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .externalRef(customer.getExternalRef())
                .name(customer.getName())
                .email(customer.getEmail())
                .plan(customer.getPlan())
                .mrrCents(customer.getMrrCents())
                .signupDate(customer.getSignupDate())
                .status(customer.getStatus())
                .createdAt(customer.getCreatedAt())
                .latestPrediction(prediction != null ? toPredictionResponse(prediction) : null)
                .build();
    }

    @SuppressWarnings("unchecked")
    public ChurnPredictionResponse toPredictionResponse(ChurnPrediction p) {
        List<ExplanationFactor> factors = p.getExplanation().stream()
                .map(m -> ExplanationFactor.builder()
                        .feature((String) m.get("feature"))
                        .impact(((Number) m.get("impact")).doubleValue())
                        .direction((String) m.get("direction"))
                        .description((String) m.getOrDefault("description", ""))
                        .build())
                .toList();

        return ChurnPredictionResponse.builder()
                .id(p.getId())
                .customerId(p.getCustomer().getId())
                .churnProbability(p.getChurnProbability())
                .riskTier(p.getRiskTier())
                .revenueAtRiskCents(p.getRevenueAtRiskCents())
                .modelVersion(p.getModelVersion())
                .explanation(factors)
                .scoredAt(p.getScoredAt())
                .build();
    }
}