FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Dépendances système nécessaires à xgboost / scikit-learn
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY . .

EXPOSE 5000

# Gunicorn en prod : 2 workers (suffisant pour scoring rapide)
CMD ["gunicorn", "-b", "0.0.0.0:5000", "-w", "2", "--timeout", "60", "app:app"]
