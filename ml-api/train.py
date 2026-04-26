"""Entraînement des modèles de détection de fraude.

Usage:
    python train.py --csv data/creditcard.csv --output models/

Pipeline:
    1. Chargement du dataset Kaggle Credit Card Fraud Detection
    2. Feature engineering (cf. ``features.py``)
    3. Split train/test stratifié
    4. SMOTE pour rééquilibrer (ratio 1:10 fraude/légitime)
    5. Entraînement XGBoost (n_estimators=300, max_depth=6, scale_pos_weight=10)
    6. Entraînement Isolation Forest (contamination=0.002, n_estimators=200)
    7. Évaluation : ROC-AUC, classification report, matrice de confusion
    8. Sauvegarde via joblib

Auteur : Équipe PFA Fraud-Detection
"""

from __future__ import annotations

import argparse
import json
import logging
import os
from pathlib import Path
from typing import Tuple

import joblib
import numpy as np
import pandas as pd
from imblearn.over_sampling import SMOTE
from sklearn.ensemble import IsolationForest
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from xgboost import XGBClassifier

from features import (
    ORDRE_FEATURES,
    StatsMontant,
    construire_features_entrainement,
)

logger = logging.getLogger("train")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s :: %(message)s",
)


# ---------------------------------------------------------------------------
# Hyperparamètres
# ---------------------------------------------------------------------------

XGB_PARAMS = {
    "n_estimators": 300,
    "max_depth": 6,
    "learning_rate": 0.1,
    "scale_pos_weight": 10,
    "tree_method": "hist",
    "eval_metric": "auc",
    "n_jobs": -1,
    "random_state": 42,
}

IF_PARAMS = {
    "n_estimators": 200,
    "contamination": 0.002,
    "max_samples": "auto",
    "n_jobs": -1,
    "random_state": 42,
}

SMOTE_RATIO = 0.10  # 1 fraude pour 10 légitimes (10%)
TEST_SIZE = 0.20


# ---------------------------------------------------------------------------
# Pipeline d'entraînement
# ---------------------------------------------------------------------------


def charger_dataset(csv_path: Path) -> pd.DataFrame:
    """Charge le CSV Kaggle et retourne le DataFrame brut.

    Args:
        csv_path: chemin vers ``creditcard.csv``.

    Returns:
        DataFrame avec colonnes V1..V28, Time, Amount, Class.

    Raises:
        FileNotFoundError: si le fichier n'existe pas.
    """
    if not csv_path.exists():
        raise FileNotFoundError(
            f"Dataset introuvable : {csv_path}. Téléchargez-le depuis Kaggle "
            "(Credit Card Fraud Detection) et placez-le dans ml-api/data/."
        )
    logger.info("Chargement du dataset depuis %s ...", csv_path)
    df = pd.read_csv(csv_path)
    logger.info("Dataset : %d lignes, %d colonnes (fraudes : %d / %.4f%%)",
                len(df), df.shape[1], int(df["Class"].sum()),
                100.0 * df["Class"].mean())
    return df


def split_features_target(df: pd.DataFrame) -> Tuple[pd.DataFrame, pd.Series]:
    """Sépare features (X) et target (y)."""
    X = df[ORDRE_FEATURES].copy()
    y = df["Class"].astype(int)
    return X, y


def equilibrer_smote(
    X_train: pd.DataFrame, y_train: pd.Series, ratio: float = SMOTE_RATIO
) -> Tuple[pd.DataFrame, pd.Series]:
    """Sur-échantillonne la classe minoritaire avec SMOTE.

    Args:
        X_train: features d'apprentissage.
        y_train: labels (0 ou 1).
        ratio: ratio cible fraude/légitime (ex. 0.1 = 10%).

    Returns:
        Tuple ``(X_resampled, y_resampled)``.
    """
    smote = SMOTE(sampling_strategy=ratio, random_state=42, n_jobs=-1)
    X_res, y_res = smote.fit_resample(X_train, y_train)
    logger.info("Après SMOTE : %d légitimes, %d fraudes (ratio=%.2f%%)",
                int((y_res == 0).sum()), int((y_res == 1).sum()),
                100.0 * y_res.mean())
    return X_res, y_res


def entrainer_xgboost(X: pd.DataFrame, y: pd.Series) -> XGBClassifier:
    """Entraîne XGBoost avec les hyperparamètres définis.

    Returns:
        Modèle XGBClassifier ajusté.
    """
    logger.info("Entraînement XGBoost (%s)...", XGB_PARAMS)
    model = XGBClassifier(**XGB_PARAMS)
    model.fit(X, y, verbose=False)
    return model


def entrainer_isolation_forest(X: pd.DataFrame) -> IsolationForest:
    """Entraîne un Isolation Forest non supervisé.

    Returns:
        Modèle IsolationForest ajusté.
    """
    logger.info("Entraînement Isolation Forest (%s)...", IF_PARAMS)
    model = IsolationForest(**IF_PARAMS)
    model.fit(X)
    return model


def evaluer(
    xgb: XGBClassifier,
    iforest: IsolationForest,
    X_test: pd.DataFrame,
    y_test: pd.Series,
) -> dict:
    """Évalue l'ensemble (xgb + iforest) sur le jeu de test.

    Le score d'ensemble est :
        ``score_final = 0.65 * xgb_proba + 0.35 * (1 - if_score_normalisé)``

    où ``if_score_normalisé`` est ramené dans [0, 1] (1 = anormal).

    Returns:
        Dictionnaire de métriques.
    """
    xgb_proba = xgb.predict_proba(X_test)[:, 1]

    if_raw = iforest.score_samples(X_test)
    if_min, if_max = float(if_raw.min()), float(if_raw.max())
    if_normalise = 1.0 - (if_raw - if_min) / max(if_max - if_min, 1e-9)

    proba_finale = 0.65 * xgb_proba + 0.35 * if_normalise
    y_pred = (proba_finale >= 0.5).astype(int)

    auc_xgb = roc_auc_score(y_test, xgb_proba)
    auc_ens = roc_auc_score(y_test, proba_finale)
    cm = confusion_matrix(y_test, y_pred).tolist()
    rapport = classification_report(y_test, y_pred, output_dict=True, zero_division=0)

    metriques = {
        "auc_xgb": float(auc_xgb),
        "auc_ensemble": float(auc_ens),
        "confusion_matrix": cm,
        "classification_report": rapport,
        "if_normalisation": {"min": if_min, "max": if_max},
    }
    logger.info("AUC XGBoost = %.4f | AUC Ensemble = %.4f", auc_xgb, auc_ens)
    logger.info("Confusion matrix : %s", cm)
    return metriques


def sauvegarder(
    xgb: XGBClassifier,
    iforest: IsolationForest,
    stats: StatsMontant,
    metriques: dict,
    output_dir: Path,
) -> None:
    """Sérialise les artefacts dans ``output_dir``."""
    output_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(xgb, output_dir / "xgb_model.pkl")
    joblib.dump(iforest, output_dir / "if_model.pkl")
    joblib.dump(stats, output_dir / "stats_montant.pkl")
    joblib.dump(
        {
            "ordre_features": ORDRE_FEATURES,
            "if_normalisation": metriques["if_normalisation"],
        },
        output_dir / "metadata.pkl",
    )
    with (output_dir / "metrics.json").open("w", encoding="utf-8") as f:
        json.dump(metriques, f, ensure_ascii=False, indent=2)
    logger.info("Modèles et métadonnées sauvegardés dans %s", output_dir)


def main() -> None:
    """Point d'entrée CLI."""
    parser = argparse.ArgumentParser(description="Entraînement détection de fraude PFA")
    parser.add_argument(
        "--csv",
        type=Path,
        default=Path(os.getenv("DATASET_CSV", "data/creditcard.csv")),
        help="Chemin vers creditcard.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("models"),
        help="Dossier de sortie pour les modèles",
    )
    args = parser.parse_args()

    df_brut = charger_dataset(args.csv)
    df_features, stats = construire_features_entrainement(df_brut)

    X, y = split_features_target(df_features)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SIZE, stratify=y, random_state=42
    )

    X_train_eq, y_train_eq = equilibrer_smote(X_train, y_train)

    xgb = entrainer_xgboost(X_train_eq, y_train_eq)
    iforest = entrainer_isolation_forest(X_train.values)

    metriques = evaluer(xgb, iforest, X_test, y_test)
    sauvegarder(xgb, iforest, stats, metriques, args.output)
    logger.info("=== Entraînement terminé ===")


if __name__ == "__main__":
    main()
