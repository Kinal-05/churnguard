package com.churnguard.service;

import com.churnguard.dto.Dtos.ChurnPredictionResponse;
import com.churnguard.dto.Dtos.PredictionCallbackRequest;
import com.churnguard.entity.ChurnPrediction;
import com.churnguard.entity.Customer;
import com.churnguard.exception.ResourceNotFoundException;
import com.churnguard.repository.ChurnPredictionRepository;
import com.churnguard.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

    private final ChurnPredictionRepository predictionRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    /**
     * Called by the ML service (via /internal/predictions) when it finishes
     * scoring a customer. Stores the prediction and evicts Redis cache so
     * the dashboard reflects the new score on the next request.
     */
    @Transactional
    @CacheEvict(value = {"dashboard-summary", "at-risk-customers"}, allEntries = true)
    public void savePrediction(PredictionCallbackRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));

        ChurnPrediction prediction = ChurnPrediction.builder()
                .customer(customer)
                .churnProbability(request.getChurnProbability())
                .riskTier(request.getRiskTier())
                .revenueAtRiskCents(request.getRevenueAtRiskCents())
                .modelVersion(request.getModelVersion())
                .explanation(request.getExplanation())
                .build();

        predictionRepository.save(prediction);

        log.info("Saved prediction for customer {} — probability={}, tier={}",
                customer.getId(), request.getChurnProbability(), request.getRiskTier());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "customer-predictions", key = "#customerId")
    public List<ChurnPredictionResponse> getPredictionHistory(UUID customerId) {
        return predictionRepository
                .findByCustomerIdOrderByScoredAtDesc(customerId)
                .stream()
                .map(customerService::toPredictionResponse)
                .toList();
    }
}