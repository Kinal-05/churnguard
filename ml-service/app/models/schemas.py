from pydantic import BaseModel, Field
from typing import Optional
from enum import Enum


class RiskTier(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class CustomerFeatures(BaseModel):
    """
    Feature vector for a single customer, computed as of scoring time.
    These are the exact features the model was trained on.
    """
    customer_id: str
    days_since_signup: int
    days_since_last_login: int
    login_count_30d: int
    login_count_90d: int
    login_trend: float          # login_count_30d / (login_count_prior_30d + 1)
    feature_use_count_30d: int
    distinct_features_used_30d: int
    support_tickets_30d: int
    support_tickets_90d: int
    payment_failures_90d: int
    mrr_cents: int
    tenure_days: int
    plan_encoded: int           # STARTER=0, PRO=1, ENTERPRISE=2


class ExplanationFactor(BaseModel):
    feature: str
    impact: float
    direction: str              # "increases_risk" | "decreases_risk"
    description: str


class PredictRequest(BaseModel):
    customer_id: str
    features: CustomerFeatures


class PredictResponse(BaseModel):
    customer_id: str
    churn_probability: float = Field(ge=0.0, le=1.0)
    risk_tier: RiskTier
    revenue_at_risk_cents: int
    model_version: str
    explanation: list[ExplanationFactor]


class TrainRequest(BaseModel):
    notes: Optional[str] = None
    use_synthetic: bool = True  # if False, trains on real event data from DB


class TrainResponse(BaseModel):
    version: str
    auc_score: float
    precision_score: float
    recall_score: float
    training_samples: int
    notes: Optional[str] = None


class ModelInfo(BaseModel):
    version: str
    auc_score: Optional[float]
    precision_score: Optional[float]
    recall_score: Optional[float]
    is_active: bool
    notes: Optional[str]


def get_risk_tier(probability: float) -> RiskTier:
    if probability >= 0.75:
        return RiskTier.CRITICAL
    elif probability >= 0.50:
        return RiskTier.HIGH
    elif probability >= 0.25:
        return RiskTier.MEDIUM
    else:
        return RiskTier.LOW