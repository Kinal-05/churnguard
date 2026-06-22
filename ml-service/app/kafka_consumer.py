"""
Kafka consumer for ChurnGuard ML service.

Consumes from the `customer.events` topic published by the Spring Boot backend.
For each event:
  1. Recomputes the customer's full feature vector from the DB
  2. Runs inference (churn probability + SHAP explanation)
  3. POSTs the result back to the Spring Boot backend via HTTP callback

This is what makes ChurnGuard event-driven rather than batch-only:
scores update automatically as customer behavior changes, without any
manual trigger or scheduled job.

The consumer runs in a background thread started by the FastAPI lifespan.
"""

import os
import json
import time
import httpx
import threading
import logging
from kafka import KafkaConsumer
from kafka.errors import KafkaConnectionError as NoBrokersAvailable

from app.core.features import compute_features_for_customer
from app.core.model_registry import registry

log = logging.getLogger("churnguard.kafka")

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
CUSTOMER_EVENTS_TOPIC = "customer.events"
CONSUMER_GROUP = "churnguard-ml-service"
BACKEND_CALLBACK_URL = os.getenv(
    "BACKEND_CALLBACK_URL",
    "http://localhost:8080/internal/predictions"
)
INTERNAL_TOKEN = os.getenv("INTERNAL_SERVICE_TOKEN", "dev_internal_token_change_in_production")


def _callback_to_backend(prediction_payload: dict):
    """POST prediction result back to Spring Boot."""
    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.post(
                BACKEND_CALLBACK_URL,
                json=prediction_payload,
                headers={"X-Internal-Token": INTERNAL_TOKEN},
            )
            response.raise_for_status()
            log.info(
                "Prediction posted for customer %s → %s",
                prediction_payload["customerId"],
                prediction_payload["riskTier"],
            )
    except Exception as e:
        log.error("Failed to callback to backend: %s", e)


def _process_event(message_value: dict):
    """Handle a single customer event message."""
    customer_id = message_value.get("customerId")
    event_type = message_value.get("eventType")

    if not customer_id:
        log.warning("Received event with no customerId, skipping")
        return

    if not registry.is_loaded():
        log.warning("Model not loaded yet, skipping scoring for customer %s", customer_id)
        return

    log.info("Processing %s event for customer %s", event_type, customer_id)

    # Recompute features from DB (includes the event we just received)
    features = compute_features_for_customer(customer_id)
    if features is None:
        log.warning("Customer %s not found in DB, skipping", customer_id)
        return

    prediction = registry.predict(features)

    # Build callback payload matching Spring Boot's PredictionCallbackRequest DTO
    payload = {
        "customerId": customer_id,
        "churnProbability": prediction.churn_probability,
        "riskTier": prediction.risk_tier.value,
        "revenueAtRiskCents": prediction.revenue_at_risk_cents,
        "modelVersion": prediction.model_version,
        "explanation": [
            {
                "feature": f.feature,
                "impact": f.impact,
                "direction": f.direction,
                "description": f.description,
            }
            for f in prediction.explanation
        ],
    }

    _callback_to_backend(payload)


def run_consumer():
    """
    Main consumer loop. Retries connecting to Kafka on startup
    (Kafka may not be ready immediately in Docker Compose).
    """
    consumer = None
    max_retries = 10
    for attempt in range(max_retries):
        try:
            consumer = KafkaConsumer(
                CUSTOMER_EVENTS_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP.split(","),
                group_id=CONSUMER_GROUP,
                auto_offset_reset="latest",
                enable_auto_commit=True,
                value_deserializer=lambda v: json.loads(v.decode("utf-8")),
                consumer_timeout_ms=1000,
            )
            log.info("Kafka consumer connected to %s", KAFKA_BOOTSTRAP)
            break
        except NoBrokersAvailable:
            log.warning(
                "Kafka not available (attempt %d/%d), retrying in 5s...",
                attempt + 1, max_retries
            )
            time.sleep(5)

    if consumer is None:
        log.error("Could not connect to Kafka after %d attempts. Consumer not started.", max_retries)
        return

    log.info("Listening on topic: %s", CUSTOMER_EVENTS_TOPIC)
    try:
        while True:
            for message in consumer:
                try:
                    _process_event(message.value)
                except Exception as e:
                    log.error("Error processing message: %s", e, exc_info=True)
    except Exception as e:
        log.error("Consumer loop crashed: %s", e, exc_info=True)
    finally:
        consumer.close()


def start_consumer_thread():
    """Start the Kafka consumer in a daemon thread."""
    thread = threading.Thread(target=run_consumer, daemon=True, name="kafka-consumer")
    thread.start()
    log.info("Kafka consumer thread started")
    return thread