from fastapi import APIRouter, HTTPException
from app.models.schemas import PredictRequest, PredictResponse, CustomerFeatures
from app.core.model_registry import registry
from app.core.features import compute_features_for_customer

router = APIRouter(prefix="/predict", tags=["Inference"])


@router.post("", response_model=PredictResponse)
def predict(request: PredictRequest):
    """
    Score a single customer's churn probability.

    Accepts a pre-computed feature vector. The Kafka consumer calls this
    internally after computing features from the live DB. External callers
    (e.g. ad-hoc scoring from the dashboard) can also POST directly.
    """
    if not registry.is_loaded():
        raise HTTPException(
            status_code=503,
            detail="No model loaded. POST to /train first."
        )
    try:
        return registry.predict(request.features)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/by-customer-id", response_model=PredictResponse)
def predict_by_customer_id(customer_id: str):
    """
    Score a customer by their UUID — computes features from DB automatically.
    Convenient for ad-hoc scoring from the dashboard or re-scoring a specific account.
    """
    if not registry.is_loaded():
        raise HTTPException(status_code=503, detail="No model loaded.")

    features = compute_features_for_customer(customer_id)
    if features is None:
        raise HTTPException(status_code=404, detail=f"Customer {customer_id} not found")

    return registry.predict(features)