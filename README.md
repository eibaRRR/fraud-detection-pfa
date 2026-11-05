> 🇬🇧 **English:** Real-time bank fraud detection system combining a **JADE multi-agent platform** (FIPA-ACL compliant), an **ML ensemble** (XGBoost + Isolation Forest) served by a Flask microservice, and a **React dashboard** fed over WebSocket. Each transaction is APPROVED, FLAGGED, or BLOCKED based on the ML score plus business rules (amount, card velocity, risk hours). End-of-year project (PFA) by a team of 4 — Docker Compose setup included.

---

# PFA · Détection intelligente de fraude financière

Système de détection de fraude bancaire en temps réel basé sur un
**Système Multi-Agents JADE** collaborant avec un pipeline **Machine Learning**
(XGBoost + Isolation Forest) et exposant un **dashboard React** alimenté
par **WebSocket**.

> Projet de Fin d'Année (PFA) — réalisé par 4 étudiants.

---

## Sommaire

1. [Description](#1-description)
2. [Architecture](#2-architecture)
3. [Stack technologique](#3-stack-technologique)
4. [Structure du projet](#4-structure-du-projet)
5. [Mise en route rapide (Docker)](#5-mise-en-route-rapide-docker)
6. [Mise en route en développement local](#6-mise-en-route-en-développement-local)
7. [Flux ACL et règles métier](#7-flux-acl-et-règles-métier)
8. [Schéma de la base de données](#8-schéma-de-la-base-de-données)
9. [API du microservice ML](#9-api-du-microservice-ml)
10. [Tests](#10-tests)
11. [Rôles de l'équipe](#11-rôles-de-léquipe)
12. [Licence](#12-licence)

---

## 1. Description

Le système analyse en temps réel un flux de transactions bancaires et
décide, pour chacune, de l'**APPROUVER**, la **SIGNALER** (vérification
humaine) ou la **BLOQUER**. La décision combine :

- un **score Machine Learning** d'ensemble (XGBoost supervisé +
  Isolation Forest non supervisé) entraîné sur le dataset Kaggle
  *Credit Card Fraud Detection* ;
- **trois règles métier** appliquées par un agent dédié (montant,
  vélocité par carte, horaire à risque) ;
- une **décision finale** prise par un agent représentant la banque
  selon des seuils calibrés.

Tous les échanges entre agents respectent le standard **FIPA-ACL**
(performatifs `REQUEST`, `INFORM`, `PROPOSE`, `ACCEPT_PROPOSAL`,
`REJECT_PROPOSAL`, `FAILURE`) et passent par le **Directory Facilitator**
JADE pour la découverte dynamique : aucune adresse n'est codée en dur.

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Plateforme JADE                              │
│                                                                      │
│  ┌────────────┐    ┌──────────────┐   ┌────────────┐  ┌────────────┐ │
│  │Coordinateur│───▶│ Surveillance │──▶│ AnalyseML  │  │   Audit    │ │
│  └────────────┘    └──────────────┘   └─────┬──────┘  └─────▲──────┘ │
│                                             │               │        │
│                                             ▼               │        │
│                                       ┌────────────┐        │        │
│                                       │   Risque   │        │        │
│                                       └─────┬──────┘        │        │
│                                             │  PROPOSE      │        │
│                                             ▼               │        │
│                                       ┌────────────┐        │        │
│                                       │   Banque   │────────┘        │
│                                       └─────┬──────┘                 │
│                                             │                        │
└─────────────────────────────────────────────┼────────────────────────┘
                                              │
                          HTTP /predict       │  WS /alertes
                                              ▼
┌────────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│   ml-api (Flask)   │    │   PostgreSQL 15  │    │  Dashboard React │
│  XGBoost + IF      │    │  Hibernate 6     │    │  Recharts + WS   │
└────────────────────┘    └──────────────────┘    └──────────────────┘
```

Les transactions arrivent via un **container JADE secondaire** (le
simulateur) qui rejoint la plateforme principale et envoie des
`REQUEST` à l'AgentSurveillanceTransaction.

---

## 3. Stack technologique

| Couche             | Technologies                                                                 |
|--------------------|-------------------------------------------------------------------------------|
| Multi-Agents       | JADE 4.6, Java 17, Maven 3.9, FIPA-ACL                                        |
| Persistance        | PostgreSQL 15, Hibernate 6, HikariCP                                          |
| ML                 | Python 3.11, Flask 3, XGBoost, scikit-learn, imbalanced-learn (SMOTE), joblib |
| Temps réel         | Jakarta WebSocket via Tyrus standalone                                        |
| Frontend           | React 18, TypeScript 5, Vite 5, TailwindCSS 3, Recharts 2, Zustand            |
| Infrastructure     | Docker, Docker Compose, Nginx (servir le SPA)                                 |
| Tests              | JUnit 5 + Mockito (Java), pytest (Python)                                     |
| Logs               | Logback JSON (Java) + logging structuré (Python)                              |

---

## 4. Structure du projet

```
fraud-detection-pfa/
├── jade-backend/          # SMA JADE + WebSocket + Hibernate
│   ├── pom.xml
│   ├── Dockerfile
│   ├── db/init.sql
│   └── src/main/java/com/pfa/fraud/
│       ├── Main.java
│       ├── agents/        # 6 agents JADE
│       ├── comportements/ # 4 behaviours ACL
│       ├── modeles/       # entités JPA
│       ├── services/      # MLClient + AlerteService
│       ├── websocket/     # Tyrus server
│       └── config/        # DatabaseConfig
├── ml-api/                # microservice Python Flask
│   ├── app.py
│   ├── train.py
│   ├── predict.py
│   ├── features.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── tests/
├── frontend/              # dashboard React
│   ├── package.json
│   ├── Dockerfile
│   ├── tailwind.config.js
│   └── src/
│       ├── App.tsx
│       ├── components/
│       ├── hooks/useWebSocket.ts
│       └── store/useStore.ts
├── simulation/            # container JADE secondaire
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/pfa/fraud/simulation/
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 5. Mise en route rapide (Docker)

### Prérequis
- Docker ≥ 24
- Docker Compose v2

### Étapes

```bash
# 1. Cloner et préparer la configuration
cp .env.example .env
# (éditer le .env si besoin — au minimum POSTGRES_PASSWORD)

# 2. Télécharger le dataset Kaggle
# https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud
# Placer creditcard.csv dans :
mkdir -p ml-api/data
# cp ~/Downloads/creditcard.csv ml-api/data/

# 3. Lancer la stack
docker compose up -d --build

# 4. Entraîner les modèles (à faire une seule fois)
docker compose exec ml-api python train.py --csv data/creditcard.csv --output models/

# 5. Redémarrer ml-api pour qu'il recharge les modèles
docker compose restart ml-api

# 6. Ouvrir le dashboard
# http://localhost:3000
```

Les services écoutent sur :
- **Frontend** : <http://localhost:3000>
- **WebSocket alertes** : `ws://localhost:8080/alertes`
- **ml-api** : <http://localhost:5000/health>
- **PostgreSQL** : `localhost:5432`

### Arrêter & nettoyer

```bash
docker compose down            # arrêt
docker compose down -v         # arrêt + suppression du volume DB
```

---

## 6. Mise en route en développement local

### jade-backend (Java)
```bash
cd jade-backend
mvn clean package -DskipTests
java -jar target/jade-backend.jar
```

### ml-api (Python)
```bash
cd ml-api
python -m venv .venv && source .venv/bin/activate   # Linux/Mac
# .\.venv\Scripts\activate                          # Windows
pip install -r requirements.txt
python train.py --csv data/creditcard.csv --output models/
flask --app app run -p 5000
```

### frontend (React)
```bash
cd frontend
npm install
npm run dev          # http://localhost:3000
```

### simulation
```bash
cd simulation
mvn clean package -DskipTests
JADE_HOST=localhost SIM_RATE=2 java -jar target/simulation.jar
```

---

## 7. Flux ACL et règles métier

### Pipeline de décision

```
Simulateur ──REQUEST──▶ Surveillance
                         └─REQUEST──▶ AnalyseML
                                       ├─INFORM──▶ Surveillance (réponse)
                                       └─INFORM──▶ Risque (forward)
                                                    └─PROPOSE──▶ Banque
                                                                  └─ACCEPT/REJECT_PROPOSAL ──▶ Risque
                                                                  ├─ persistance DB
                                                                  ├─ broadcast WebSocket
                                                                  └─INFORM──▶ Audit
```

### Règles métier (AgentEvaluateurRisque)

| Code | Règle                                              | Pondération |
|------|----------------------------------------------------|:-----------:|
| R1   | Montant > 5 000 €                                  | + 0.20      |
| R2   | Plus de 3 transactions sur la carte en 1 h         | + 0.25      |
| R3   | Heure entre 01h00 et 04h00 UTC                     | + 0.15      |

`score_final = min(1.0, fraud_probability + Σ ajustements)`

### Seuils de décision (AgentBanque)

| Score              | Performatif        | Action      |
|--------------------|--------------------|-------------|
| ≥ 0.75             | ACCEPT_PROPOSAL    | **BLOQUER**     |
| 0.50 – 0.749       | ACCEPT_PROPOSAL    | **SIGNALER**    |
| < 0.50             | REJECT_PROPOSAL    | **APPROUVER**   |

---

## 8. Schéma de la base de données

Quatre tables principales (cf. `jade-backend/db/init.sql`) :

- `transactions` — historique des transactions analysées
- `alertes` — alertes générées (action ≠ APPROUVER)
- `decisions_agents` — journal des décisions agent par agent
- `messages_acl` — trace de tous les échanges ACL (auditabilité)

Plus une vue `v_kpi_fraude` agrégeant les KPI affichés par le dashboard.

---

## 9. API du microservice ML

| Méthode | Endpoint    | Description                                      |
|---------|-------------|--------------------------------------------------|
| POST    | `/predict`  | Score une transaction (body JSON)                |
| GET     | `/health`   | Statut de santé + chargement des modèles         |
| GET     | `/metrics`  | Métriques de la dernière évaluation (AUC, CM…)   |

Exemple :
```bash
curl -X POST http://localhost:5000/predict \
  -H "Content-Type: application/json" \
  -d '{"id":"abc","montant":4500,"carte_fin":"1234","horodatage":"2025-04-26T02:30:00Z"}'
```

Réponse :
```json
{
  "fraud_probability": 0.87,
  "xgb_score": 0.81,
  "if_score": 0.95,
  "features_used": 34
}
```

---

## 10. Tests

### Java (JUnit 5 + Mockito)
```bash
cd jade-backend
mvn test
```

### Python (pytest)
```bash
cd ml-api
pytest -v
```

### Frontend (type-check)
```bash
cd frontend
npm run type-check
```

---

## 11. Rôles de l'équipe

> 4 étudiants — répartition suggérée en fonction des modules.

| Membre   | Module principal             | Responsabilités                                                  |
|----------|------------------------------|------------------------------------------------------------------|
| Étu. 1   | Backend SMA JADE             | Agents, comportements, ontologie, DF, protocoles FIPA            |
| Étu. 2   | Pipeline ML                  | Feature engineering, entraînement, ensemble XGB+IF, API Flask    |
| Étu. 3   | Frontend & UX                | Dashboard React, WebSocket, charts, design dark                  |
| Étu. 4   | Données & DevOps             | Schéma SQL, Hibernate, simulateur, Docker, CI, README            |

---

## 12. Licence

Projet académique réalisé dans le cadre d'un PFA. Toute réutilisation
externe est libre à des fins pédagogiques avec mention de l'équipe
auteure.
