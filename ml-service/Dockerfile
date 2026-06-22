FROM python:3.11-slim

WORKDIR /app

# Install system dependencies needed by lightgbm and psycopg2
RUN apt-get update && apt-get install -y \
    libgomp1 \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies first (cached layer)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/
COPY training/ ./training/

# Model artifacts directory (mounted as volume in docker-compose)
RUN mkdir -p /app/model-artifacts

EXPOSE 8000

# Train model on first startup if no artifact exists, then serve
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]