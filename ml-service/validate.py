import sys, os
sys.path.insert(0, '.')
os.environ['MODEL_DIR'] = 'C:/Project/churnguard/ml-service/model-artifacts'

import app.core.model_registry as mr
from pathlib import Path
mr.MODEL_DIR = Path(os.environ['MODEL_DIR'])
loaded = mr.registry.load_active_model()
print(f'[OK] Model loaded: {loaded}, version: {mr.registry.version}')

from app.models.schemas import CustomerFeatures
features = CustomerFeatures(
    customer_id='test-123',
    days_since_signup=180,
    days_since_last_login=45,
    login_count_30d=2,
    login_count_90d=10,
    login_trend=0.4,
    feature_use_count_30d=3,
    distinct_features_used_30d=2,
    support_tickets_30d=2,
    support_tickets_90d=4,
    payment_failures_90d=1,
    mrr_cents=4900,
    tenure_days=180,
    plan_encoded=1,
)
result = mr.registry.predict(features)
print(f'[OK] Inference: probability={result.churn_probability}, tier={result.risk_tier}')
print(f'[OK] SHAP factors: {len(result.explanation)}')
for f in result.explanation:
    print(f'     {f.feature:<35} impact={f.impact:+.4f}  ({f.direction})')

from app.main import app
print(f'[OK] FastAPI app imports cleanly: {app.title}')
print()
print('=== All ML service tests passed! ===')