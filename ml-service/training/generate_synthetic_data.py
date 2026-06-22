"""
Synthetic data generator for ChurnGuard model training.

Generates a realistic churn dataset where churn probability is correlated
with behavioral signals in a way that mirrors real SaaS churn patterns:
  - Low login frequency → higher churn risk
  - Payment failures → strong churn signal
  - High support ticket volume → moderate churn signal
  - Short tenure + low feature usage → higher churn risk
  - High MRR customers churn less (more invested in the product)

The dataset is synthetic but the correlations are grounded in real SaaS
churn research. This is documented clearly in the README so reviewers
understand the methodology rather than questioning the data source.
"""

import numpy as np
import pandas as pd
from datetime import datetime, timedelta


def generate_synthetic_churn_dataset(
    n_customers: int = 2000,
    seed: int = 42,
) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    # ── Base customer attributes ───────────────────────────────────────────────
    tenure_days = rng.integers(30, 730, size=n_customers)
    plan_encoded = rng.choice([0, 1, 2], size=n_customers, p=[0.4, 0.4, 0.2])
    mrr_cents = np.where(
        plan_encoded == 0, rng.integers(500, 1500, size=n_customers),
        np.where(
            plan_encoded == 1, rng.integers(3000, 7000, size=n_customers),
            rng.integers(15000, 35000, size=n_customers)
        )
    )

    # ── Behavioral features (correlated with churn risk) ──────────────────────
    # Base engagement score: higher = more engaged = less likely to churn
    engagement = rng.beta(2, 2, size=n_customers)  # 0-1, centered around 0.5

    # Login counts: engaged customers log in more
    login_count_30d = np.clip(
        (engagement * 20 + rng.normal(0, 3, n_customers)).astype(int), 0, 60
    )
    login_count_90d = np.clip(
        (engagement * 55 + rng.normal(0, 8, n_customers)).astype(int), 0, 180
    )

    # Days since last login: disengaged customers have stale logins
    days_since_last_login = np.clip(
        ((1 - engagement) * 60 + rng.exponential(5, n_customers)).astype(int), 0, tenure_days
    )

    # Login trend: declining engagement = ratio < 1
    prior_logins = np.clip(login_count_90d - login_count_30d, 0, None)
    login_trend = login_count_30d / (prior_logins + 1)

    # Feature usage
    feature_use_count_30d = np.clip(
        (engagement * 15 + rng.normal(0, 3, n_customers)).astype(int), 0, 50
    )
    distinct_features_used_30d = np.clip(
        (engagement * 5 + rng.normal(0, 1, n_customers)).astype(int), 0, 10
    )

    # Support tickets: frustrated customers open more tickets
    frustration = rng.beta(1, 4, size=n_customers)  # skewed low
    support_tickets_30d = np.clip(
        (frustration * 5 + rng.poisson(0.3, n_customers)).astype(int), 0, 15
    )
    support_tickets_90d = np.clip(
        support_tickets_30d + (frustration * 8 + rng.poisson(0.5, n_customers)).astype(int), 0, 30
    )

    # Payment failures: strong churn signal
    payment_fail_prob = 0.05 + (1 - engagement) * 0.20
    payment_failures_90d = rng.binomial(3, payment_fail_prob, size=n_customers)

    days_since_signup = tenure_days

    # ── Churn label generation ─────────────────────────────────────────────────
    # Logistic model: log-odds of churn as a linear combination of risk factors
    log_odds = (
        -2.0                                          # base rate ~12% churn
        + 0.04  * days_since_last_login               # stale = risky
        - 0.08  * login_count_30d                     # active = safe
        + 0.60  * payment_failures_90d                # strong signal
        + 0.25  * support_tickets_30d                 # moderate signal
        - 0.30  * login_trend                         # improving trend = safer
        - 0.06  * feature_use_count_30d               # feature adoption = sticky
        - 0.0001 * mrr_cents                          # high MRR = less likely to churn
        - 0.003 * tenure_days                         # longer tenure = more loyal
        + rng.normal(0, 0.5, n_customers)             # noise
    )
    churn_probability = 1 / (1 + np.exp(-log_odds))
    churned = (rng.uniform(size=n_customers) < churn_probability).astype(int)

    df = pd.DataFrame({
        "days_since_signup": days_since_signup,
        "days_since_last_login": days_since_last_login,
        "login_count_30d": login_count_30d,
        "login_count_90d": login_count_90d,
        "login_trend": login_trend.round(4),
        "feature_use_count_30d": feature_use_count_30d,
        "distinct_features_used_30d": distinct_features_used_30d,
        "support_tickets_30d": support_tickets_30d,
        "support_tickets_90d": support_tickets_90d,
        "payment_failures_90d": payment_failures_90d,
        "mrr_cents": mrr_cents,
        "tenure_days": tenure_days,
        "plan_encoded": plan_encoded,
        "churned": churned,
    })

    churn_rate = churned.mean()
    print(f"Generated {n_customers} customers | churn rate: {churn_rate:.1%}")
    return df


if __name__ == "__main__":
    df = generate_synthetic_churn_dataset()
    print(df.head())
    print(df["churned"].value_counts())