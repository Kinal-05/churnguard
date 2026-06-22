"""
ChurnGuard ML Service — FastAPI application entry point.

Startup sequence:
  1. Load the latest trained model artifact into memory
  2. Start the Kafka consumer thread (listens for customer events)
  3. Serve inference and training endpoints

The model is loaded once at startup — not per-request. SHAP TreeExplainer
is also initialized once since it's expensive to build.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.model_registry import registry
from app.kafka_consumer import start_consumer_thread
from app.routers import predict, train

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
log = logging.getLogger("churnguard.main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown logic."""
    # ── Startup ───────────────────────────────────────────────────
    log.info("ChurnGuard ML Service starting up...")

    # Load the latest trained model
    loaded = registry.load_active_model()
    if loaded:
        log.info("Model loaded: version=%s", registry.version)
    else:
        log.warning(
            "No model artifact found in model-artifacts/. "
            "POST to /train to train and load one."
        )

    # Start Kafka consumer in background thread
    start_consumer_thread()

    yield

    # ── Shutdown ──────────────────────────────────────────────────
    log.info("ChurnGuard ML Service shutting down...")


app = FastAPI(
    title="ChurnGuard ML Service",
    description=(
        "Churn prediction service powering ChurnGuard. "
        "Exposes LightGBM inference with SHAP explanations "
        "and is fed by a Kafka event stream from the Spring Boot backend."
    ),
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ───────────────────────────────────────────────────────────────────
app.include_router(predict.router)
app.include_router(train.router)


# ── Health ────────────────────────────────────────────────────────────────────
@app.get("/health", tags=["Health"])
def health():
    return {
        "status": "ok",
        "model_loaded": registry.is_loaded(),
        "model_version": registry.version,
    }


@app.get("/", tags=["Health"])
def root():
    return {
        "service": "ChurnGuard ML Service",
        "docs": "/docs",
        "health": "/health",
    }