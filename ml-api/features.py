"""Feature engineering pour la détection de fraude.

Ce module est partagé entre l'entraînement (``train.py``) et l'inférence
en ligne (``predict.py``). Il garantit que les transformations appliquées
au moment du scoring sont strictement identiques à celles utilisées au
moment de l'apprentissage (cohérence train/serve).

Features produites :
    - Features brutes V1..V28 du dataset Kaggle (PCA-anonymisées)
    - Z-score robuste du montant (basé sur median + IQR)
    - Encodage cyclique de l'heure : sin(2π·h/24), cos(2π·h/24)
    - Vélocité par carte : nombre de transactions sur 1 h et 24 h

Auteur : Équipe PFA Fraud-Detection
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Deque, Dict, Iterable, List, Tuple

import numpy as np
import pandas as pd

# Colonnes V1..V28 issues du dataset Kaggle Credit Card Fraud Detection.
COLONNES_PCA: List[str] = [f"V{i}" for i in range(1, 29)]

# Ordre canonique des colonnes du vecteur final fourni au modèle.
# Toute modification ici doit être répliquée à l'inférence.
ORDRE_FEATURES: List[str] = (
    COLONNES_PCA
    + [
        "Amount",
        "amount_zscore",
        "hour_sin",
        "hour_cos",
        "velocity_1h",
        "velocity_24h",
    ]
)


# ---------------------------------------------------------------------------
# Statistiques globales (calibrées à l'entraînement, sérialisées avec le modèle)
# ---------------------------------------------------------------------------


@dataclass
class StatsMontant:
    """Statistiques robustes du montant utilisées pour le z-score.

    Attributes:
        median: médiane des montants observés à l'entraînement.
        iqr: écart interquartile (Q3 - Q1) ; sert d'estimateur robuste de l'écart-type.
    """

    median: float
    iqr: float

    def zscore(self, amount: float) -> float:
        """Calcule le z-score robuste : (x - median) / max(iqr, 1e-6)."""
        return float((amount - self.median) / max(self.iqr, 1e-6))


def calculer_stats_montant(amounts: Iterable[float]) -> StatsMontant:
    """Calcule les stats robustes du montant à partir d'un échantillon.

    Args:
        amounts: itérable de montants (en euros).

    Returns:
        Un objet ``StatsMontant`` (median + iqr).
    """
    series = pd.Series(list(amounts), dtype="float64")
    q1, q3 = series.quantile([0.25, 0.75]).tolist()
    return StatsMontant(median=float(series.median()), iqr=float(q3 - q1))


# ---------------------------------------------------------------------------
# Encodage cyclique de l'heure
# ---------------------------------------------------------------------------


def encoder_heure(heure: float) -> Tuple[float, float]:
    """Encode une heure (0-23) en deux composantes cycliques sin/cos.

    Args:
        heure: heure du jour, peut être fractionnaire.

    Returns:
        Tuple ``(sin, cos)`` dans [-1, 1].
    """
    angle = 2.0 * np.pi * (heure % 24.0) / 24.0
    return float(np.sin(angle)), float(np.cos(angle))


def time_to_hour(time_seconds: float) -> float:
    """Convertit la colonne ``Time`` du dataset Kaggle (secondes depuis t0)
    en heure de la journée modulo 24.

    Args:
        time_seconds: nombre de secondes depuis la première transaction.

    Returns:
        L'heure du jour [0, 24).
    """
    return float((time_seconds / 3600.0) % 24.0)


# ---------------------------------------------------------------------------
# Vélocité par carte (état mémoire pour l'inférence en ligne)
# ---------------------------------------------------------------------------


@dataclass
class VelocityTracker:
    """Compteur sliding-window des transactions par carte.

    Pour chaque identifiant de carte, on conserve les horodatages des
    transactions récentes dans deux fenêtres : 1 h et 24 h.

    Attributes:
        window_1h_seconds: taille de la fenêtre courte en secondes.
        window_24h_seconds: taille de la fenêtre longue en secondes.
        history: dict carte → deque de timestamps (epoch seconds).
    """

    window_1h_seconds: int = 3600
    window_24h_seconds: int = 86_400
    history: Dict[str, Deque[float]] = field(default_factory=dict)

    def observe(self, carte: str, timestamp_s: float) -> Tuple[int, int]:
        """Enregistre une nouvelle transaction et retourne les compteurs.

        Args:
            carte: identifiant de la carte (4 derniers chiffres ou hash).
            timestamp_s: horodatage epoch en secondes.

        Returns:
            Tuple ``(nb_1h, nb_24h)`` : nombre de transactions sur la carte
            au cours des 1 h et 24 h précédentes (transaction courante incluse).
        """
        dq = self.history.setdefault(carte, deque())
        # Purge les entrées hors fenêtre 24 h
        cutoff_24h = timestamp_s - self.window_24h_seconds
        while dq and dq[0] < cutoff_24h:
            dq.popleft()
        dq.append(timestamp_s)
        cutoff_1h = timestamp_s - self.window_1h_seconds
        nb_1h = sum(1 for t in dq if t >= cutoff_1h)
        nb_24h = len(dq)
        return nb_1h, nb_24h


# ---------------------------------------------------------------------------
# Construction du DataFrame d'entraînement
# ---------------------------------------------------------------------------


def construire_features_entrainement(df: pd.DataFrame) -> Tuple[pd.DataFrame, StatsMontant]:
    """Construit le DataFrame de features à partir du dataset brut Kaggle.

    Le dataset doit contenir les colonnes V1..V28, ``Amount``, ``Time`` et ``Class``.

    Args:
        df: DataFrame brut chargé depuis ``creditcard.csv``.

    Returns:
        Un tuple ``(features_df, stats_montant)`` où ``features_df`` contient
        toutes les colonnes listées dans ``ORDRE_FEATURES`` plus ``Class``,
        et ``stats_montant`` est l'objet à sérialiser avec le modèle.
    """
    requis = set(COLONNES_PCA) | {"Amount", "Time", "Class"}
    manquants = requis - set(df.columns)
    if manquants:
        raise ValueError(f"Colonnes manquantes dans le dataset : {sorted(manquants)}")

    df = df.sort_values("Time").reset_index(drop=True).copy()
    stats = calculer_stats_montant(df["Amount"].tolist())
    df["amount_zscore"] = df["Amount"].apply(stats.zscore)

    heures = df["Time"].apply(time_to_hour)
    sincos = heures.apply(encoder_heure)
    df["hour_sin"] = sincos.apply(lambda t: t[0])
    df["hour_cos"] = sincos.apply(lambda t: t[1])

    # Le dataset Kaggle est anonymisé : il n'y a pas d'identifiant de carte.
    # On synthétise des "cartes" fictives pour pouvoir simuler la vélocité
    # de manière reproductible (sinon ces features sont nulles).
    rng = np.random.default_rng(seed=42)
    df["_carte_simulee"] = rng.integers(low=0, high=10_000, size=len(df)).astype(str)

    tracker = VelocityTracker()
    v1h: List[int] = []
    v24h: List[int] = []
    for carte, t in zip(df["_carte_simulee"].tolist(), df["Time"].tolist()):
        n1, n24 = tracker.observe(carte, float(t))
        v1h.append(n1)
        v24h.append(n24)
    df["velocity_1h"] = v1h
    df["velocity_24h"] = v24h
    df.drop(columns=["_carte_simulee"], inplace=True)

    return df[ORDRE_FEATURES + ["Class"]], stats


# ---------------------------------------------------------------------------
# Construction d'un vecteur unique pour l'inférence en ligne
# ---------------------------------------------------------------------------


def construire_vecteur_inference(
    transaction: dict,
    stats: StatsMontant,
    tracker: VelocityTracker,
) -> np.ndarray:
    """Convertit une transaction reçue par l'API en vecteur de features.

    Le payload attendu est tolérant : les colonnes V1..V28 sont facultatives
    (on remplit par 0.0) ; en pratique le simulateur les fournit, et un
    appel "métier" (montant + marchand seulement) reste possible.

    Args:
        transaction: dict JSON contenant au minimum ``montant``, ``carte_fin``,
            et idéalement ``horodatage`` (ISO-8601) et V1..V28.
        stats: stats du montant calibrées à l'entraînement.
        tracker: compteur de vélocité partagé.

    Returns:
        Vecteur numpy 2D de shape (1, n_features) prêt pour ``model.predict_proba``.
    """
    valeurs: List[float] = []
    for col in COLONNES_PCA:
        valeurs.append(float(transaction.get(col, 0.0)))

    montant = float(transaction.get("montant") or transaction.get("Amount") or 0.0)
    valeurs.append(montant)
    valeurs.append(stats.zscore(montant))

    heure = _heure_depuis_transaction(transaction)
    sin, cos = encoder_heure(heure)
    valeurs.append(sin)
    valeurs.append(cos)

    carte = str(transaction.get("carte_fin") or "0000")
    timestamp_s = _timestamp_depuis_transaction(transaction)
    nb_1h, nb_24h = tracker.observe(carte, timestamp_s)
    valeurs.append(float(nb_1h))
    valeurs.append(float(nb_24h))

    return np.asarray(valeurs, dtype="float64").reshape(1, -1)


def _heure_depuis_transaction(transaction: dict) -> float:
    """Extrait l'heure depuis le champ ``horodatage`` (ISO-8601) ou ``Time``."""
    if "horodatage" in transaction and transaction["horodatage"]:
        try:
            ts = pd.to_datetime(transaction["horodatage"], utc=True)
            return float(ts.hour) + ts.minute / 60.0
        except (ValueError, TypeError):
            pass
    if "Time" in transaction:
        return time_to_hour(float(transaction["Time"]))
    return float(pd.Timestamp.utcnow().hour)


def _timestamp_depuis_transaction(transaction: dict) -> float:
    """Convertit le horodatage en epoch seconds (fallback : maintenant)."""
    if "horodatage" in transaction and transaction["horodatage"]:
        try:
            return float(pd.to_datetime(transaction["horodatage"], utc=True).timestamp())
        except (ValueError, TypeError):
            pass
    return float(pd.Timestamp.utcnow().timestamp())
