"""
Feature engineering for churn prediction.

Given a customer_id, queries the PostgreSQL event log and computes
the exact feature vector the model was trained on. Keeping feature
engineering here (Python) rather than in the Java backend means:
  - Features are computed the same way at training time and serving time
    (no train/serve skew)
  - Feature logic can evolve independently of the Java service
"""

import pandas as pd
import numpy as np
from datetime import datetime, timezone, timedelta
from typing import Optional
from sqlalchemy import create_engine, text
import os

from app.models.schemas import CustomerFeatures

PLAN_ENCODING = {"STARTER": 0, "PRO": 1, "ENTERPRISE": 2}

def get_db_engine():
    db_url = os.getenv(
        "DATABASE_URL",
        "postgresql://churnguard:churnguard_dev_password@localhost:5432/churnguard"
    )
    return create_engine(db_url)


def compute_features_for_customer(customer_id: str) -> Optional[CustomerFeatures]:
    """
    Compute the full feature vector for a customer from the live DB.
    Called during real-time inference when the ML service receives a
    scoring request from Kafka or the /predict endpoint.
    """
    engine = get_db_engine()
    now = datetime.now(timezone.utc)
    cutoff_30d = now - timedelta(days=30)
    cutoff_60d = now - timedelta(days=60)
    cutoff_90d = now - timedelta(days=90)

    with engine.connect() as conn:
        # Customer base info
        customer_row = conn.execute(text("""
            SELECT id, mrr_cents, signup_date, plan, status
            FROM customers
            WHERE id = :customer_id
        """), {"customer_id": customer_id}).fetchone()

        if customer_row is None:
            return None

        mrr_cents = customer_row.mrr_cents or 0
        signup_date = customer_row.signup_date
        plan = customer_row.plan or "STARTER"
        plan_encoded = PLAN_ENCODING.get(plan.upper(), 0)
        days_since_signup = (now.date() - signup_date).days
        tenure_days = days_since_signup

        # Event counts
        events = conn.execute(text("""
            SELECT event_type, occurred_at
            FROM customer_events
            WHERE customer_id = :customer_id
            AND occurred_at >= :since_90d
            ORDER BY occurred_at DESC
        """), {
            "customer_id": customer_id,
            "since_90d": cutoff_90d
        }).fetchall()

        df = pd.DataFrame(events, columns=["event_type", "occurred_at"])

        def count_events(event_type: str, since: datetime) -> int:
            if df.empty:
                return 0
            mask = (df["event_type"] == event_type) & (df["occurred_at"] >= since)
            return int(mask.sum())

        login_count_30d = count_events("LOGIN", cutoff_30d)
        login_count_60d = count_events("LOGIN", cutoff_60d)
        login_count_90d = count_events("LOGIN", cutoff_90d)

        # Login trend: ratio of recent 30d logins vs prior 30d (60d–30d window)
        prior_30d_logins = login_count_60d - login_count_30d
        login_trend = login_count_30d / (prior_30d_logins + 1)

        feature_use_30d = count_events("FEATURE_USE", cutoff_30d)

        # Distinct features used in 30d
        if df.empty:
            distinct_features_30d = 0
        else:
            fu_mask = (df["event_type"] == "FEATURE_USE") & (df["occurred_at"] >= cutoff_30d)
            distinct_features_30d = int(fu_mask.sum())  # simplified; payload parsing in v2

        support_30d = count_events("SUPPORT_TICKET", cutoff_30d)
        support_90d = count_events("SUPPORT_TICKET", cutoff_90d)
        payment_failures_90d = count_events("PAYMENT_FAILED", cutoff_90d)

        # Days since last login
        login_rows = df[df["event_type"] == "LOGIN"]
        if login_rows.empty:
            days_since_last_login = tenure_days  # never logged in = max staleness
        else:
            last_login = login_rows["occurred_at"].max()
            if hasattr(last_login, 'tzinfo') and last_login.tzinfo is None:
                last_login = last_login.replace(tzinfo=timezone.utc)
            days_since_last_login = (now - last_login).days

    return CustomerFeatures(
        customer_id=customer_id,
        days_since_signup=days_since_signup,
        days_since_last_login=days_since_last_login,
        login_count_30d=login_count_30d,
        login_count_90d=login_count_90d,
        login_trend=round(login_trend, 4),
        feature_use_count_30d=feature_use_30d,
        distinct_features_used_30d=distinct_features_30d,
        support_tickets_30d=support_30d,
        support_tickets_90d=support_90d,
        payment_failures_90d=payment_failures_90d,
        mrr_cents=mrr_cents,
        tenure_days=tenure_days,
        plan_encoded=plan_encoded,
    )


def features_to_array(features: CustomerFeatures) -> np.ndarray:
    """Convert a CustomerFeatures object to the numpy array the model expects."""
    return np.array([[
        features.days_since_signup,
        features.days_since_last_login,
        features.login_count_30d,
        features.login_count_90d,
        features.login_trend,
        features.feature_use_count_30d,
        features.distinct_features_used_30d,
        features.support_tickets_30d,
        features.support_tickets_90d,
        features.payment_failures_90d,
        features.mrr_cents,
        features.tenure_days,
        features.plan_encoded,
    ]], dtype=np.float64)


FEATURE_NAMES = [
    "days_since_signup",
    "days_since_last_login",
    "login_count_30d",
    "login_count_90d",
    "login_trend",
    "feature_use_count_30d",
    "distinct_features_used_30d",
    "support_tickets_30d",
    "support_tickets_90d",
    "payment_failures_90d",
    "mrr_cents",
    "tenure_days",
    "plan_encoded",
]

FEATURE_DESCRIPTIONS = {
    "days_since_last_login": "Days since last login",
    "login_count_30d": "Logins in last 30 days",
    "login_count_90d": "Logins in last 90 days",
    "login_trend": "Login frequency trend (recent vs prior 30d)",
    "feature_use_count_30d": "Feature usage events in last 30 days",
    "distinct_features_used_30d": "Distinct features used in last 30 days",
    "support_tickets_30d": "Support tickets in last 30 days",
    "support_tickets_90d": "Support tickets in last 90 days",
    "payment_failures_90d": "Payment failures in last 90 days",
    "mrr_cents": "Monthly recurring revenue (cents)",
    "tenure_days": "Days since signup",
    "days_since_signup": "Days since signup",
    "plan_encoded": "Subscription plan tier",
}