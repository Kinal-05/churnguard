package com.churnguard.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${churnguard.kafka.topics.customer-events}")
    private String customerEventsTopic;

    /**
     * Publishes a customer event to Kafka so the ML service can consume it
     * for feature recomputation and scoring — decoupled from the ingestion
     * request path so a slow/down ML service never blocks event ingestion.
     */
    public void publishCustomerEvent(UUID customerId, String eventType,
                                      Map<String, Object> payload, String tenantId) {
        Map<String, Object> message = Map.of(
                "customerId", customerId.toString(),
                "eventType", eventType,
                "payload", payload != null ? payload : Map.of(),
                "tenantId", tenantId
        );

        kafkaTemplate.send(customerEventsTopic, customerId.toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event for customer {}: {}",
                                customerId, ex.getMessage());
                    } else {
                        log.debug("Published {} event for customer {} to partition {}",
                                eventType, customerId,
                                result.getRecordMetadata().partition());
                    }
                });
    }
}