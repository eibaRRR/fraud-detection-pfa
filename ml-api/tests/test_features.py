"""Tests unitaires de feature engineering."""

from __future__ import annotations

import math

import pytest

from features import (
    StatsMontant,
    VelocityTracker,
    encoder_heure,
    time_to_hour,
)


def test_zscore_robuste_zero_quand_mediane():
    stats = StatsMontant(median=100.0, iqr=50.0)
    assert stats.zscore(100.0) == pytest.approx(0.0)


def test_zscore_positif_au_dessus_mediane():
    stats = StatsMontant(median=100.0, iqr=50.0)
    assert stats.zscore(200.0) == pytest.approx(2.0)


def test_zscore_iqr_nul_ne_divise_pas_par_zero():
    stats = StatsMontant(median=10.0, iqr=0.0)
    assert math.isfinite(stats.zscore(50.0))


@pytest.mark.parametrize(
    "heure, sin_attendu, cos_attendu",
    [
        (0.0, 0.0, 1.0),
        (6.0, 1.0, 0.0),
        (12.0, 0.0, -1.0),
        (18.0, -1.0, 0.0),
    ],
)
def test_encodage_horaire(heure, sin_attendu, cos_attendu):
    s, c = encoder_heure(heure)
    assert s == pytest.approx(sin_attendu, abs=1e-9)
    assert c == pytest.approx(cos_attendu, abs=1e-9)


def test_time_to_hour_modulo():
    assert time_to_hour(0) == 0.0
    assert time_to_hour(3600) == 1.0
    assert time_to_hour(86400) == 0.0  # 24h pile = retour à 0


def test_velocity_tracker_compte_fenetre_1h():
    tracker = VelocityTracker()
    n1, n24 = tracker.observe("CARD-1", 1000.0)
    assert (n1, n24) == (1, 1)
    n1, n24 = tracker.observe("CARD-1", 1500.0)  # +500s
    assert (n1, n24) == (2, 2)
    # +2h plus tard : la première sort de la fenêtre 1h
    n1, n24 = tracker.observe("CARD-1", 1500.0 + 7200)
    assert n1 == 1
    assert n24 == 3


def test_velocity_tracker_isole_les_cartes():
    tracker = VelocityTracker()
    tracker.observe("A", 100.0)
    tracker.observe("A", 200.0)
    n1, _ = tracker.observe("B", 300.0)
    assert n1 == 1  # carte B indépendante
