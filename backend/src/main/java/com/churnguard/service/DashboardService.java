package com.churnguard.service;

import com.churnguard.dto.Dtos.*;
import com.churnguard.entity.ChurnPrediction;
import com.churnguard.entity.Customer;
import com.churnguard.repository.ChurnPredictionRepository;
import com.churnguard.repository.CustomerRepository;
import com.churnguard.repository.ModelVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ChurnPredictionRepository predictionRepository;
    private final CustomerRepository customerRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final CustomerService customerService;

    /**
     * Summary stats for the dashboard header cards.
     * Cached in Redis for 60s — this query touches every customer's latest
     * prediction so it's expensive at scale.
     */
    @Cacheable(value = "dashboard-summary", key = "#tenantId")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UUID tenantId) {
        List<ChurnPrediction> latestPredictions =
                predictionRepository.findLatestPredictionsForTenant(tenantId);

        long critical = count(latestPredictions, ChurnPrediction.RiskTier.CRITICAL);
        long high     = count(latestPredictions, ChurnPrediction.RiskTier.HIGH);
        long medium   = count(latestPredictions, ChurnPrediction.RiskTier.MEDIUM);
        long low      = count(latestPredictions, ChurnPrediction.RiskTier.LOW);

        long totalRevenueAtRisk = latestPredictions.stream()
                .filter(p -> p.getRiskTier() == ChurnPrediction.RiskTier.HIGH
                          || p.getRiskTier() == ChurnPrediction.RiskTier.CRITICAL)
                .mapToLong(ChurnPrediction::getRevenueAtRiskCents)
                .sum();

        long totalActive = customerRepository.countByTenantIdAndStatus(
                tenantId, Customer.CustomerStatus.ACTIVE);

        String activeModel = modelVersionRepository.findByIsActiveTrue()
                .map(m -> m.getVersion())
                .orElse("none");

        return DashboardSummaryResponse.builder()
                .totalActiveCustomers(totalActive)
                .criticalRiskCount(critical)
                .highRiskCount(high)
                .mediumRiskCount(medium)
                .lowRiskCount(low)
                .totalRevenueAtRiskCents(totalRevenueAtRisk)
                .activeModelVersion(activeModel)
                .build();
    }

    /**
     * Ranked list of at-risk customers for the dashboard table.
     * Also cached — sorted by churn_probability DESC so the highest-risk
     * accounts appear first.
     */
    @Cacheable(value = "at-risk-customers", key = "#tenantId + '-' + #limit")
    @Transactional(readOnly = true)
    public List<AtRiskCustomerResponse> getAtRiskCustomers(UUID tenantId, int limit) {
        List<ChurnPrediction> predictions =
                predictionRepository.findLatestPredictionsForTenantPaged(
                        tenantId, PageRequest.of(0, limit)).getContent();

        return predictions.stream()
                .filter(p -> p.getRiskTier() != ChurnPrediction.RiskTier.LOW)
                .map(p -> {
                    Customer c = p.getCustomer();
                    List<ExplanationFactor> topFactors =
                            customerService.toPredictionResponse(p).getExplanation()
                            .stream().limit(3).toList();

                    return AtRiskCustomerResponse.builder()
                            .customerId(c.getId())
                            .customerName(c.getName())
                            .plan(c.getPlan())
                            .mrrCents(c.getMrrCents())
                            .churnProbability(p.getChurnProbability())
                            .riskTier(p.getRiskTier())
                            .revenueAtRiskCents(p.getRevenueAtRiskCents())
                            .topFactors(topFactors)
                            .scoredAt(p.getScoredAt())
                            .build();
                })
                .toList();
    }

    private long count(List<ChurnPrediction> predictions, ChurnPrediction.RiskTier tier) {
        return predictions.stream().filter(p -> p.getRiskTier() == tier).count();
    }
}