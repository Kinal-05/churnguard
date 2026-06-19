package com.churnguard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "churn_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChurnPrediction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "churn_probability", nullable = false)
    private Double churnProbability;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier", nullable = false)
    private RiskTier riskTier;

    @Column(name = "revenue_at_risk_cents", nullable = false)
    private Long revenueAtRiskCents;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    // List of {feature, impact, direction} maps, stored as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> explanation;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    @PrePersist
    protected void onCreate() {
        if (scoredAt == null) {
            scoredAt = Instant.now();
        }
    }

    public enum RiskTier {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}