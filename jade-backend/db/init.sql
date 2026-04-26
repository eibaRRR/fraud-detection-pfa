-- ============================================================
-- Script d'initialisation PostgreSQL pour le système PFA
-- de détection intelligente de fraude.
-- Exécuté automatiquement via /docker-entrypoint-initdb.d/
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------
-- Table : transactions
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    horodatage   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    montant      DECIMAL(12,2) NOT NULL,
    marchand     VARCHAR(255),
    carte_fin    VARCHAR(4),
    banque_id    VARCHAR(50),
    label_fraude BOOLEAN DEFAULT FALSE,
    probabilite  DECIMAL(5,4),
    statut       VARCHAR(20)
        CHECK (statut IN ('EN_ATTENTE', 'APPROUVE', 'SIGNALE', 'BLOQUE'))
);

CREATE INDEX IF NOT EXISTS idx_transactions_horodatage ON transactions(horodatage DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_statut    ON transactions(statut);
CREATE INDEX IF NOT EXISTS idx_transactions_carte     ON transactions(carte_fin);

-- ------------------------------------------------------------
-- Table : messages_acl (journal d'audit des échanges agents)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages_acl (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    horodatage      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    agent_emetteur  VARCHAR(100) NOT NULL,
    agent_recepteur VARCHAR(100) NOT NULL,
    performatif     VARCHAR(50)  NOT NULL,
    ontologie       VARCHAR(100),
    contenu_json    JSONB
);

CREATE INDEX IF NOT EXISTS idx_acl_horodatage ON messages_acl(horodatage DESC);
CREATE INDEX IF NOT EXISTS idx_acl_emetteur   ON messages_acl(agent_emetteur);

-- ------------------------------------------------------------
-- Table : alertes
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alertes (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id     UUID REFERENCES transactions(id) ON DELETE CASCADE,
    agent_declencheur  VARCHAR(100),
    score_risque       DECIMAL(5,4),
    regles_declenchees JSONB,
    action_effectuee   VARCHAR(20),
    resolue_le         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_alertes_action ON alertes(action_effectuee);
CREATE INDEX IF NOT EXISTS idx_alertes_score  ON alertes(score_risque DESC);

-- ------------------------------------------------------------
-- Table : decisions_agents
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS decisions_agents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alerte_id    UUID REFERENCES alertes(id) ON DELETE CASCADE,
    agent_id     VARCHAR(100) NOT NULL,
    decision     VARCHAR(50)  NOT NULL,
    raisonnement TEXT,
    confiance    DECIMAL(5,4),
    cree_le      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_decisions_agent ON decisions_agents(agent_id);
CREATE INDEX IF NOT EXISTS idx_decisions_date  ON decisions_agents(cree_le DESC);

-- ------------------------------------------------------------
-- Vue de KPI temps réel pour le dashboard
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_kpi_fraude AS
SELECT
    (SELECT COUNT(*) FROM transactions)                                  AS total_transactions,
    (SELECT COUNT(*) FROM alertes WHERE action_effectuee = 'BLOQUER')    AS total_bloquees,
    (SELECT COUNT(*) FROM alertes WHERE action_effectuee = 'SIGNALER')   AS total_signalees,
    (SELECT COALESCE(AVG(score_risque), 0) FROM alertes)                 AS score_moyen;
