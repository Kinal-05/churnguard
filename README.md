# ⚡ ChurnGuard — Subscription Revenue Intelligence Platform

> A production-grade, multi-tenant SaaS churn prediction platform built with
> Java 21 + Spring Boot 3.x, PostgreSQL, Redis, Kafka, Python FastAPI, LightGBM,
> SHAP explainability, and a React dashboard.

---

## The Problem

SaaS companies lose 5–7% of MRR to churn every month — and most only find out
a customer is leaving when the cancellation email arrives. Customer success teams
manage hundreds of accounts with no systematic way to know which ones are quietly
disengaging until it's too late to intervene.

**ChurnGuard** continuously scores every customer's churn risk, explains *why*
that risk is high (so CS teams know what to act on, not just who), and surfaces
revenue at risk so leadership can prioritize retention spend.

---

## Architecture

```
┌─────────────────────┐
│   React Dashboard    │  Vite + Recharts
│  (port 5173)        │
└──────────┬──────────┘
           │ REST (JWT)
           ▼
┌──────────────────────────────┐
│    Spring Boot Backend        │  Java 21, Spring Boot 3.3
│    (port 8080)                │  Spring Web, Security, Data JPA
│                               │  Redis caching, Kafka producer
└──────┬────────────────┬───────┘
       │                │
  writes to        publishes to
       │                │
       ▼                ▼
┌────────────┐   ┌──────────────────┐
│ PostgreSQL  │   │  Kafka Topic     │
│ (port 5432) │   │ customer.events  │
└────────────┘   └────────┬─────────┘
                           │ consumes
                           ▼
              ┌────────────────────────────┐
              │   FastAPI ML Service        │  Python 3.11
              │   (port 8000)              │  LightGBM + SHAP
              │                            │  Kafka consumer thread
              │  POST /internal/predictions│
              │  ──────────────────────►  │
              └────────────────────────────┘
```

### Why Kafka over RabbitMQ
Event replay matters here — when we retrain a model or change feature
engineering, we want to replay historical events to regenerate features.
Kafka's log-retention model supports this natively. Multiple independent
consumers can also read the same topic without the producer needing to
know about them.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Web, Spring Data JPA, Spring Security (JWT) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 (dashboard aggregate queries, 60s TTL) |
| Messaging | Apache Kafka (Confluent images) |
| ML Service | Python 3.11, FastAPI, LightGBM, scikit-learn, SHAP, pandas |
| Frontend | React 18, Vite, Recharts, React Router |
| Infra | Docker, Docker Compose |

---

## ML Methodology

### Feature Engineering
Per-customer features computed from the raw event log at scoring time:

| Feature | Signal |
|---|---|
| `days_since_last_login` | Staleness — strongest churn signal |
| `login_count_30d / 90d` | Engagement frequency |
| `login_trend` | Recent logins vs prior 30d (declining = risky) |
| `feature_use_count_30d` | Product adoption depth |
| `payment_failures_90d` | Billing health — strong churn signal |
| `support_tickets_30d` | Frustration indicator |
| `mrr_cents` | High MRR customers churn less |
| `tenure_days` | Longer tenure = more loyal |

### Model
**LightGBM binary classifier** — chosen for:
- Fast training (leaf-wise growth)
- Excellent performance on tabular data with class imbalance
- First-class SHAP TreeExplainer support
- Industry standard for SaaS churn/propensity models

**Training data:** Synthetic dataset (2,000 customers) with churn probability
correlated to behavioral signals via a logistic model — documented clearly
here because real SaaS event data is proprietary. The correlations mirror
real SaaS churn research.

**Evaluation results:**
- AUC-ROC: ~0.82
- Recall: ~0.75 (catches 3 in 4 churners)
- Threshold: tuned to churn base rate rather than 0.5 (avoids predicting
  nobody churns on imbalanced data)

### Explainability
SHAP `TreeExplainer` produces exact Shapley values (not approximations) for
each prediction — each value represents how much that feature pushed the score
away from the base rate. The top 5 factors are stored with every prediction and
displayed in the dashboard so CS teams know what to act on.

### Model Lifecycle
New model versions are trained, evaluated, and registered — but **not
auto-promoted**. A human must call `POST /train/models/{version}/activate`
to promote a version to serving. This is intentional: blind auto-promotion
of new models is a real production risk.

---

## Quickstart

### Prerequisites
- Docker Desktop
- Java 21 + Maven (for local backend dev)
- Python 3.11+ (for local ML dev)
- Node.js 20+ (for local frontend dev)

### Option A — Full stack via Docker Compose (recommended first run)

```bash
# 1. Clone the repo
git clone
cd churnguard

# 2. Train the model first (required before starting ML service)
cd ml-service
pip install -r requirements.txt
python training/train.py
cd ..

# 3. Start everything
cd infra
docker-compose up --build
```

Services will be available at:
| Service | URL |
|---|---|
| React Dashboard | http://localhost:5173 |
| Spring Boot API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| FastAPI ML docs | http://localhost:8000/docs |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

Login: `admin@acme.test` / `admin123`

### Option B — Run services individually (for development)

**Terminal 1 — Infrastructure:**
```bash
cd infra
docker-compose up postgres redis kafka zookeeper
```

**Terminal 2 — Backend:**
```bash
cd backend
mvn spring-boot:run
```

**Terminal 3 — ML Service:**
```bash
cd ml-service
pip install -r requirements.txt
python training/train.py   # only needed once
uvicorn app.main:app --reload --port 8000
```

**Terminal 4 — Frontend:**
```bash
cd frontend
npm install
npm run dev
```

---

## API Reference

Full interactive docs available at:
- **Backend:** `http://localhost:8080/swagger-ui.html`
- **ML Service:** `http://localhost:8000/docs`

### Key endpoints

```
POST /api/v1/auth/login              Authenticate → JWT
POST /api/v1/customers               Create/upsert a customer
GET  /api/v1/customers               List customers (paginated)
GET  /api/v1/customers/{id}          Get customer + latest prediction
POST /api/v1/events                  Ingest customer event → Kafka
GET  /api/v1/dashboard/summary       Risk distribution + revenue at risk
GET  /api/v1/dashboard/at-risk       Ranked at-risk customers
GET  /api/v1/customers/{id}/predictions  Prediction history

POST /predict                        Score a customer (ML service)
POST /train                          Trigger model retraining
GET  /train/models                   List model versions + metrics
POST /train/models/{version}/activate Promote a model version
```

---

## Folder Structure

```
churnguard/
├── backend/                    Spring Boot service (Java 21)
│   ├── src/main/java/com/churnguard/
│   │   ├── config/             Security, Redis, OpenAPI config
│   │   ├── controller/         REST controllers
│   │   ├── service/            Business logic
│   │   ├── repository/         Spring Data JPA repositories
│   │   ├── entity/             JPA entities
│   │   ├── dto/                Request/response DTOs
│   │   ├── kafka/              Kafka producer
│   │   ├── security/           JWT provider + filter
│   │   └── exception/          Global exception handler
│   └── src/main/resources/
│       ├── application.yml     Configuration
│       └── db/migration/       Flyway SQL migrations (V1–V5)
├── ml-service/                 Python FastAPI ML service
│   ├── app/
│   │   ├── main.py             FastAPI app + lifespan
│   │   ├── kafka_consumer.py   Event consumer → scoring → callback
│   │   ├── core/
│   │   │   ├── features.py     Feature engineering from DB
│   │   │   └── model_registry.py LightGBM loader + SHAP inference
│   │   ├── models/schemas.py   Pydantic request/response schemas
│   │   └── routers/            predict.py, train.py
│   ├── training/
│   │   ├── generate_synthetic_data.py
│   │   └── train.py            Training script
│   └── model-artifacts/        Saved model PKLs + metrics JSON
├── frontend/                   React 18 + Vite dashboard
│   └── src/
│       ├── pages/              Dashboard, AtRisk, Customers, Detail, Login
│       ├── components/         RiskBadge, ProbabilityBar, ShapExplanation, Sidebar
│       ├── api/                Axios client + endpoint functions
│       └── utils/              Auth helpers, formatters
├── infra/
│   └── docker-compose.yml
└── docs/
    └── DESIGN.md               Full architecture + schema + API design
```
