"""
Model registry and inference engine.

Loads the active LightGBM model from disk and exposes predict() and explain().
Designed to be a singleton loaded once at startup — not re-created per request.
"""

import os
import json
import shap
import joblib
import numpy as np
from pathlib import Path
from typing import Optional

from app.core.features import (
    FEATURE_NAMES,
    FEATURE_DESCRIPTIONS,
    features_to_array,
)
from app.models.schemas import (
    CustomerFeatures,
    ExplanationFactor,
    PredictResponse,
    RiskTier,
    get_risk_tier,
)

MODEL_DIR = Path(os.getenv("MODEL_DIR", "/app/model-artifacts"))


class ModelRegistry:
    """
    Holds the currently active model in memory.
    Call load_active_model() at startup and after retraining.
    """

    def __init__(self):
        self.model = None
        self.explainer = None
        self.version: Optional[str] = None
        self.metrics: dict = {}

    def load_active_model(self) -> bool:
        """
        Looks for the newest model artifact in MODEL_DIR.
        Returns True if a model was loaded, False if none exists yet.
        """
        model_files = sorted(MODEL_DIR.glob("churn_model_v*.pkl"), reverse=True)
        if not model_files:
            return False

        latest = model_files[0]
        self.model = joblib.load(latest)

        # Build SHAP TreeExplainer once — expensive, so we cache it on the instance
        self.explainer = shap.TreeExplainer(self.model)

        # Parse version from filename: churn_model_v3.pkl → "v3"
        self.version = latest.stem.replace("churn_model_", "")

        # Load metrics if available
        metrics_path = MODEL_DIR / f"metrics_{self.version}.json"
        if metrics_path.exists():
            with open(metrics_path) as f:
                self.metrics = json.load(f)

        return True

    def is_loaded(self) -> bool:
        return self.model is not None

    def predict(
        self,
        features: CustomerFeatures,
        mrr_cents: Optional[int] = None,
    ) -> PredictResponse:
        if not self.is_loaded():
            raise RuntimeError("No model loaded. Run /train first.")

        X = features_to_array(features)
        churn_probability = float(self.model.predict_proba(X)[0][1])
        risk_tier = get_risk_tier(churn_probability)

        # Revenue at risk: full MRR for CRITICAL/HIGH, 50% for MEDIUM, 0 for LOW
        effective_mrr = mrr_cents or features.mrr_cents
        rev_multiplier = {
            RiskTier.CRITICAL: 1.0,
            RiskTier.HIGH: 1.0,
            RiskTier.MEDIUM: 0.5,
            RiskTier.LOW: 0.0,
        }[risk_tier]
        revenue_at_risk_cents = int(effective_mrr * rev_multiplier)

        explanation = self._explain(X, churn_probability)

        return PredictResponse(
            customer_id=features.customer_id,
            churn_probability=round(churn_probability, 4),
            risk_tier=risk_tier,
            revenue_at_risk_cents=revenue_at_risk_cents,
            model_version=self.version,
            explanation=explanation,
        )

    def _explain(self, X: np.ndarray, probability: float) -> list[ExplanationFactor]:
        """
        Compute SHAP values for a single prediction and return the top 5
        factors sorted by absolute impact.

        SHAP TreeExplainer gives exact Shapley values for tree models — not
        approximations. Each value represents how much that feature pushed
        the prediction away from the base rate.
        """
        shap_values = self.explainer.shap_values(X)

        # For binary classification, shap_values is a list [class0, class1]
        # We want class 1 (churn) values
        if isinstance(shap_values, list):
            churn_shap = shap_values[1][0]
        else:
            churn_shap = shap_values[0]

        factors = []
        for i, (name, shap_val) in enumerate(zip(FEATURE_NAMES, churn_shap)):
            direction = "increases_risk" if shap_val > 0 else "decreases_risk"
            description = FEATURE_DESCRIPTIONS.get(name, name)

            factors.append(ExplanationFactor(
                feature=name,
                impact=round(float(shap_val), 4),
                direction=direction,
                description=description,
            ))

        # Sort by absolute impact, return top 5
        factors.sort(key=lambda f: abs(f.impact), reverse=True)
        return factors[:5]

    def list_model_versions(self) -> list[dict]:
        versions = []
        for model_file in sorted(MODEL_DIR.glob("churn_model_v*.pkl"), reverse=True):
            version = model_file.stem.replace("churn_model_", "")
            metrics_path = MODEL_DIR / f"metrics_{version}.json"
            metrics = {}
            if metrics_path.exists():
                with open(metrics_path) as f:
                    metrics = json.load(f)

            versions.append({
                "version": version,
                "is_active": version == self.version,
                **metrics,
            })
        return versions


# Module-level singleton — imported by routers and kafka consumer
registry = ModelRegistry()