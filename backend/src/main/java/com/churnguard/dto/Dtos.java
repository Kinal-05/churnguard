package com.churnguard.dto;

import com.churnguard.entity.ChurnPrediction.RiskTier;
import com.churnguard.entity.Customer.CustomerStatus;
import com.churnguard.entity.CustomerEvent.EventType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All DTOs in one file, organized by domain.
 * In a larger project these would be split into sub-packages,
 * but keeping them together makes the API surface easy to review.
 */
public class Dtos {

    // ─────────────────────────────────────────────────────────
    // AUTH
    // ─────────────────────────────────────────────────────────

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 6)
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String email;
        private String role;
        private UUID tenantId;
        private long expiresInMs;
    }

    // ─────────────────────────────────────────────────────────
    // CUSTOMERS
    // ─────────────────────────────────────────────────────────

    @Data
    public static class UpsertCustomerRequest {
        @NotBlank
        private String externalRef;

        @NotBlank
        private String name;

        @Email
        private String email;

        private String plan;

        @Min(0)
        private Long mrrCents = 0L;

        @NotNull
        private LocalDate signupDate;

        private CustomerStatus status = CustomerStatus.ACTIVE;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerResponse {
        private UUID id;
        private String externalRef;
        private String name;
        private String email;
        private String plan;
        private Long mrrCents;
        private LocalDate signupDate;
        private CustomerStatus status;
        private Instant createdAt;
        // Latest churn prediction, null if not yet scored
        private ChurnPredictionResponse latestPrediction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagedCustomerResponse {
        private List<CustomerResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    // ─────────────────────────────────────────────────────────
    // EVENTS
    // ─────────────────────────────────────────────────────────

    @Data
    public static class IngestEventRequest {
        @NotBlank
        private String customerExternalRef;

        @NotNull
        private EventType eventType;

        private Map<String, Object> eventPayload;

        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventResponse {
        private Long id;
        private UUID customerId;
        private EventType eventType;
        private Map<String, Object> eventPayload;
        private Instant occurredAt;
        private Instant createdAt;
    }

    // ─────────────────────────────────────────────────────────
    // PREDICTIONS
    // ─────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChurnPredictionResponse {
        private UUID id;
        private UUID customerId;
        private Double churnProbability;
        private RiskTier riskTier;
        private Long revenueAtRiskCents;
        private String modelVersion;
        private List<ExplanationFactor> explanation;
        private Instant scoredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExplanationFactor {
        private String feature;
        private Double impact;      // SHAP value — magnitude = importance
        private String direction;   // "increases_risk" | "decreases_risk"
        private String description; // human-readable, e.g. "No login in 45 days"
    }

    // ─────────────────────────────────────────────────────────
    // DASHBOARD
    // ─────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardSummaryResponse {
        private long totalActiveCustomers;
        private long criticalRiskCount;
        private long highRiskCount;
        private long mediumRiskCount;
        private long lowRiskCount;
        private long totalRevenueAtRiskCents;
        private String activeModelVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtRiskCustomerResponse {
        private UUID customerId;
        private String customerName;
        private String plan;
        private Long mrrCents;
        private Double churnProbability;
        private RiskTier riskTier;
        private Long revenueAtRiskCents;
        private List<ExplanationFactor> topFactors;
        private Instant scoredAt;
    }

    // ─────────────────────────────────────────────────────────
    // INTERNAL (ML service → backend callback)
    // ─────────────────────────────────────────────────────────

    @Data
    public static class PredictionCallbackRequest {
        @NotNull
        private UUID customerId;

        @NotNull
        @DecimalMin("0.0") @DecimalMax("1.0")
        private Double churnProbability;

        @NotNull
        private RiskTier riskTier;

        @NotNull
        private Long revenueAtRiskCents;

        @NotBlank
        private String modelVersion;

        @NotNull
        private List<Map<String, Object>> explanation;
    }

    // ─────────────────────────────────────────────────────────
    // COMMON
    // ─────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private Instant timestamp;
    }
}