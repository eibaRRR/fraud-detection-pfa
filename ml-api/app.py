"""Microservice Flask exposant les modèles de détection de fraude.

Endpoints:
    POST /predict   : score une transaction
    GET  /health    : statut de santé
    GET  /metrics   : métriques d'évaluation du dernier entraînement

Lancement:
    flask run                     # dev
    gunicorn -b 0.0.0.0:5000 app:app  # prod (Docker)

Auteur : Équipe PFA Fraud-Detection
"""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path

from flask import Flask, jsonify, request

from predict import FraudPredictor

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

MODELS_DIR = Path(os.getenv("MODELS_DIR", "models"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

logging.basicConfig(
    level=LOG_LEVEL,
    format='{"ts":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","msg":"%(message)s"}',
)
logger = logging.getLogger("ml-api")

# Champs requis dans le payload pour pouvoir scorer.
CHAMPS_REQUIS = ("montant",)


# ---------------------------------------------------------------------------
# Application Flask
# ---------------------------------------------------------------------------

app = Flask(__name__)
predictor = FraudPredictor(MODELS_DIR)


def _charger_modeles_si_possible() -> None:
    """Tente de charger les modèles ; ne crashe pas si absents."""
    try:
        predictor.charger()
        logger.info("Modèles chargés au démarrage.")
    except FileNotFoundError as e:
        logger.warning("Modèles non disponibles : %s", e)


_charger_modeles_si_possible()


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.route("/health", methods=["GET"])
def health():
    """Retourne le statut de santé du microservice."""
    return jsonify(
        {
            "status": "ok",
            "models_loaded": predictor.loaded,
            "models_dir": str(MODELS_DIR),
        }
    )


@app.route("/predict", methods=["POST"])
def predict():
    """Score une transaction reçue en JSON.

    Body attendu : objet JSON contenant au minimum ``montant``.
    """
    if not predictor.loaded:
        return _erreur(503, "models_not_loaded",
                       "Les modèles ne sont pas chargés. "
                       "Lancez d'abord train.py.")

    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return _erreur(400, "bad_payload", "Body JSON invalide ou manquant.")

    manquants = [c for c in CHAMPS_REQUIS if c not in payload]
    if manquants:
        return _erreur(400, "missing_fields",
                       f"Champs manquants : {manquants}")

    try:
        result = predictor.predict(payload)
    except Exception as exc:  # noqa: BLE001
        logger.exception("Échec scoring")
        return _erreur(500, "scoring_failed", str(exc))

    logger.info(
        "predict tx=%s montant=%s -> proba=%s",
        payload.get("id", "?"),
        payload.get("montant"),
        result["fraud_probability"],
    )
    return jsonify(result)


@app.route("/metrics", methods=["GET"])
def metrics():
    """Retourne les métriques d'évaluation du dernier entraînement."""
    metrics_path = MODELS_DIR / "metrics.json"
    if not metrics_path.exists():
        return _erreur(404, "no_metrics", "Aucune métrique disponible.")
    with metrics_path.open("r", encoding="utf-8") as f:
        return jsonify(json.load(f))


def _erreur(code: int, error: str, message: str):
    """Construit une réponse d'erreur normalisée."""
    return jsonify({"error": error, "message": message}), code


if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    app.run(host="0.0.0.0", port=port, debug=False)
