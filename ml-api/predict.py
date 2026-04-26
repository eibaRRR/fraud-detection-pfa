"""Inférence en ligne pour la détection de fraude.

Charge les modèles sérialisés par ``train.py`` et expose une fonction
``predict(transaction)`` utilisée par le serveur Flask (``app.py``).

Auteur : Équipe PFA Fraud-Detection
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from xgboost import XGBClassifier

from features import (
    StatsMontant,
    VelocityTracker,
    construire_vecteur_inference,
)

logger = logging.getLogger("predict")

# Pondérations de l'ensemble (cohérent avec train.py).
POIDS_XGB = 0.65
POIDS_IF = 0.35


@dataclass
class ArtefactsML:
    """Conteneur des artefacts chargés en mémoire."""

    xgb: XGBClassifier
    iforest: IsolationForest
    stats: StatsMontant
    if_min: float
    if_max: float


class FraudPredictor:
    """Prédicteur thread-safe utilisé par l'API Flask.

    Exemple:
        >>> predictor = FraudPredictor(Path("models"))
        >>> predictor.charger()
        >>> predictor.predict({"montant": 4500, "carte_fin": "1234"})
        {'fraud_probability': 0.82, 'xgb_score': 0.78, 'if_score': 0.55, ...}
    """

    def __init__(self, models_dir: Path) -> None:
        self._models_dir = models_dir
        self._artefacts: Optional[ArtefactsML] = None
        self._tracker = VelocityTracker()
        self._lock = threading.Lock()

    @property
    def loaded(self) -> bool:
        """True si les modèles sont chargés en mémoire."""
        return self._artefacts is not None

    def charger(self) -> None:
        """Charge les modèles depuis le disque (idempotent).

        Raises:
            FileNotFoundError: si l'un des fichiers .pkl est manquant.
        """
        with self._lock:
            if self._artefacts is not None:
                return
            xgb_path = self._models_dir / "xgb_model.pkl"
            if_path = self._models_dir / "if_model.pkl"
            stats_path = self._models_dir / "stats_montant.pkl"
            meta_path = self._models_dir / "metadata.pkl"

            for p in (xgb_path, if_path, stats_path, meta_path):
                if not p.exists():
                    raise FileNotFoundError(f"Artefact manquant : {p}")

            xgb = joblib.load(xgb_path)
            iforest = joblib.load(if_path)
            stats = joblib.load(stats_path)
            metadata = joblib.load(meta_path)
            if_norm = metadata["if_normalisation"]

            self._artefacts = ArtefactsML(
                xgb=xgb,
                iforest=iforest,
                stats=stats,
                if_min=float(if_norm["min"]),
                if_max=float(if_norm["max"]),
            )
            logger.info("Modèles chargés depuis %s", self._models_dir)

    def predict(self, transaction: dict) -> dict:
        """Calcule le score de fraude pour une transaction.

        Args:
            transaction: dict JSON envoyé par le backend JADE.

        Returns:
            Dictionnaire ``{fraud_probability, xgb_score, if_score, features_used}``.

        Raises:
            RuntimeError: si les modèles ne sont pas chargés.
        """
        if self._artefacts is None:
            raise RuntimeError("Modèles non chargés (appeler charger() au démarrage)")

        art = self._artefacts
        x = construire_vecteur_inference(transaction, art.stats, self._tracker)

        # XGBoost : probabilité de la classe positive (fraude).
        xgb_proba = float(art.xgb.predict_proba(x)[0, 1])

        # Isolation Forest : score brut normalisé en [0, 1] (1 = anormal).
        if_raw = float(art.iforest.score_samples(x)[0])
        if_norm = self._normaliser_if(if_raw, art.if_min, art.if_max)

        score_final = POIDS_XGB * xgb_proba + POIDS_IF * if_norm
        score_final = float(np.clip(score_final, 0.0, 1.0))

        return {
            "fraud_probability": round(score_final, 6),
            "xgb_score": round(xgb_proba, 6),
            "if_score": round(if_norm, 6),
            "features_used": int(x.shape[1]),
        }

    @staticmethod
    def _normaliser_if(raw: float, mn: float, mx: float) -> float:
        """Normalise le score Isolation Forest dans [0, 1] (1 = anormal).

        On utilise la calibration min/max apprise à l'entraînement et on
        clippe pour gérer les valeurs hors distribution.
        """
        plage = max(mx - mn, 1e-9)
        norm = 1.0 - (raw - mn) / plage
        return float(np.clip(norm, 0.0, 1.0))
