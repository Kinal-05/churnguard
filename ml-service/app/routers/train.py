from fastapi import APIRouter, HTTPException, BackgroundTasks
from app.models.schemas import TrainRequest, TrainResponse, ModelInfo
from app.core.model_registry import registry

router = APIRouter(prefix="/train", tags=["Training"])


def _run_training(notes: str, n_samples: int):
    """Runs in a background thread so the POST returns immediately."""
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).parent.parent.parent))
    from training.train import train
    metrics = train(notes=notes, n_samples=n_samples)
    # Auto-load the newly trained model
    registry.load_active_model()
    return metrics


@router.post("", response_model=TrainResponse)
def trigger_training(request: TrainRequest, background_tasks: BackgroundTasks):
    """
    Trigger a training run. Runs in the background — returns immediately.

    Note: the new model is NOT auto-promoted as the active serving model
    until you call POST /models/{version}/activate. This is intentional —
    blind auto-promotion of new models is a real production risk.
    Instead, review metrics first, then activate manually.
    """
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).parent.parent.parent))
    from training.train import train

    # Run synchronously for simplicity (returns when done)
    # In production this would be a Celery task or similar
    try:
        metrics = train(notes=request.notes, n_samples=2000)
        registry.load_active_model()
        return TrainResponse(**metrics)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Training failed: {str(e)}")


@router.get("/models", response_model=list[ModelInfo])
def list_models():
    """List all trained model versions with their evaluation metrics."""
    versions = registry.list_model_versions()
    return [ModelInfo(**v) for v in versions]


@router.post("/models/{version}/activate")
def activate_model(version: str):
    """
    Promote a model version to active (serving) status.
    Loads the chosen version into memory immediately.
    """
    from pathlib import Path
    import os
    model_dir = Path(os.getenv("MODEL_DIR", "/app/model-artifacts"))
    model_path = model_dir / f"churn_model_{version}.pkl"

    if not model_path.exists():
        raise HTTPException(status_code=404, detail=f"Model version {version} not found")

    import joblib
    import shap
    registry.model = joblib.load(model_path)
    registry.explainer = shap.TreeExplainer(registry.model)
    registry.version = version

    return {"message": f"Model {version} is now active", "version": version}