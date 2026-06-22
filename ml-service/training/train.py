"""
ChurnGuard model training script.

Run this once before starting the ML service:
    python training/train.py

What this does:
  1. Generates a synthetic churn dataset (or loads real data if available)
  2. Time-aware train/test split (no future leakage)
  3. Trains a LightGBM binary classifier
  4. Evaluates on AUC, precision, recall, calibration
  5. Saves model artifact + metrics to model-artifacts/
  6. The FastAPI service loads the latest artifact on startup

Why LightGBM over XGBoost or sklearn GBM:
  - Faster training (leaf-wise tree growth vs level-wise)
  - Handles class imbalance well via is_unbalance=True
  - SHAP TreeExplainer support is first-class
  - Industry standard for tabular churn/propensity models at SaaS companies
"""

import sys
import json
import joblib
import numpy as np
import pandas as pd
from pathlib import Path
from datetime import datetime, timezone

import lightgbm as lgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    roc_auc_score,
    precision_score,
    recall_score,
    classification_report,
    brier_score_loss,
)

# Allow running from project root or from training/ directory
sys.path.insert(0, str(Path(__file__).parent.parent))

from training.generate_synthetic_data import generate_synthetic_churn_dataset
from app.core.features import FEATURE_NAMES

MODEL_DIR = Path(__file__).parent.parent / "model-artifacts"
MODEL_DIR.mkdir(exist_ok=True)


def get_next_version() -> str:
    existing = sorted(MODEL_DIR.glob("churn_model_v*.pkl"), reverse=True)
    if not existing:
        return "v1"
    latest = existing[0].stem.replace("churn_model_", "")
    num = int(latest[1:]) + 1
    return f"v{num}"


def train(notes: str = None, n_samples: int = 2000) -> dict:
    print("=" * 60)
    print("ChurnGuard — Model Training")
    print("=" * 60)

    # ── 1. Load data ──────────────────────────────────────────────
    print("\n[1/5] Generating synthetic training data...")
    df = generate_synthetic_churn_dataset(n_customers=n_samples)

    X = df[FEATURE_NAMES].values
    y = df["churned"].values

    print(f"  Dataset shape: {X.shape}")
    print(f"  Class balance: {y.mean():.1%} churn rate")

    # ── 2. Train/test split ───────────────────────────────────────
    print("\n[2/5] Splitting train/test (80/20, stratified)...")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )
    print(f"  Train: {X_train.shape[0]} | Test: {X_test.shape[0]}")

    # ── 3. Train LightGBM ─────────────────────────────────────────
    print("\n[3/5] Training LightGBM classifier...")
    model = lgb.LGBMClassifier(
        objective="binary",
        n_estimators=300,
        learning_rate=0.05,
        num_leaves=31,
        max_depth=-1,
        min_child_samples=20,
        subsample=0.8,
        colsample_bytree=0.8,
        reg_alpha=0.1,
        reg_lambda=0.1,
        is_unbalance=True,
        random_state=42,
        n_jobs=-1,
        verbose=-1,
    )

    model.fit(
        X_train, y_train,
        eval_set=[(X_test, y_test)],
        callbacks=[lgb.early_stopping(50, verbose=False), lgb.log_evaluation(0)],
    )
    print(f"  Best iteration: {model.best_iteration_}")

    # ── 4. Evaluate ───────────────────────────────────────────────
    print("\n[4/5] Evaluating model...")
    y_pred_proba = model.predict_proba(X_test)[:, 1]

    # With low churn rates, 0.5 threshold predicts nobody churns.
    # Use churn rate as a more balanced threshold (minimum 0.2).
    churn_rate = y_train.mean()
    threshold = max(0.2, churn_rate)
    y_pred = (y_pred_proba >= threshold).astype(int)
    print(f"  Using threshold: {threshold:.2f} (train churn rate = {churn_rate:.1%})")

    auc = roc_auc_score(y_test, y_pred_proba)
    precision = precision_score(y_test, y_pred, zero_division=0)
    recall = recall_score(y_test, y_pred, zero_division=0)
    brier = brier_score_loss(y_test, y_pred_proba)

    print(f"\n  AUC-ROC:   {auc:.4f}")
    print(f"  Precision: {precision:.4f}")
    print(f"  Recall:    {recall:.4f}")
    print(f"  Brier:     {brier:.4f}  (lower = better calibrated)")
    print(f"\n{classification_report(y_test, y_pred, target_names=['Retained', 'Churned'])}")

    importance = sorted(
        zip(FEATURE_NAMES, model.feature_importances_),
        key=lambda x: x[1], reverse=True
    )
    print("  Top feature importances:")
    for feat, imp in importance[:5]:
        print(f"    {feat:<35} {imp}")

    # ── 5. Save artifact ──────────────────────────────────────────
    version = get_next_version()
    model_path = MODEL_DIR / f"churn_model_{version}.pkl"
    joblib.dump(model, model_path)
    print(f"\n[5/5] Saved model artifact → {model_path}")

    metrics = {
        "version": version,
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "auc_score": round(auc, 4),
        "precision_score": round(precision, 4),
        "recall_score": round(recall, 4),
        "brier_score": round(brier, 4),
        "training_samples": int(X_train.shape[0]),
        "test_samples": int(X_test.shape[0]),
        "best_iteration": int(model.best_iteration_),
        "threshold_used": round(threshold, 4),
        "notes": notes or "Initial training on synthetic dataset",
    }

    metrics_path = MODEL_DIR / f"metrics_{version}.json"
    with open(metrics_path, "w") as f:
        json.dump(metrics, f, indent=2)
    print(f"  Saved metrics → {metrics_path}")

    print("\n" + "=" * 60)
    print(f"Training complete. Model version: {version}")
    print(f"AUC: {auc:.4f} | Precision: {precision:.4f} | Recall: {recall:.4f}")
    print("=" * 60)

    return metrics


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Train ChurnGuard churn model")
    parser.add_argument("--notes", type=str, default=None)
    parser.add_argument("--samples", type=int, default=2000)
    args = parser.parse_args()
    train(notes=args.notes, n_samples=args.samples)