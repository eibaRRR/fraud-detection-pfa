import { create } from 'zustand';
import type {
  AlerteEnveloppe,
  DecisionPayload,
  EtatAgent,
  KpiSysteme,
  StatutTransaction,
  Transaction,
} from '../types';

const TAILLE_MAX_FLUX = 200;

interface StoreState {
  transactions: Transaction[];
  alertes: DecisionPayload[];
  agents: EtatAgent[];
  kpi: KpiSysteme;
  ajouterAlerte: (env: AlerteEnveloppe) => void;
  reinitialiser: () => void;
}

const AGENTS_INITIAUX: EtatAgent[] = [
  { nom: 'Coordinateur', role: 'Orchestrateur',          statut: 'ACTIF' },
  { nom: 'Surveillance', role: 'Ingestion transactions', statut: 'ACTIF' },
  { nom: 'AnalyseML',    role: 'Scoring XGBoost + IF',   statut: 'ACTIF' },
  { nom: 'Risque',       role: 'Règles métier',          statut: 'ACTIF' },
  { nom: 'Banque-BNP',   role: 'Décision finale',        statut: 'ACTIF' },
  { nom: 'Audit',        role: 'Traçabilité ACL',        statut: 'ACTIF' },
];

const KPI_INITIAL: KpiSysteme = {
  totalTransactions: 0,
  fraudesDetectees: 0,
  tauxFauxPositifs: 0,
  tempsMoyenDecisionMs: 0,
};

const actionVersStatut = (a: DecisionPayload['action']): StatutTransaction => {
  switch (a) {
    case 'BLOQUER':   return 'BLOQUE';
    case 'SIGNALER':  return 'SIGNALE';
    case 'APPROUVER': return 'APPROUVE';
  }
};

export const useStore = create<StoreState>((set) => ({
  transactions: [],
  alertes: [],
  agents: AGENTS_INITIAUX,
  kpi: KPI_INITIAL,

  ajouterAlerte: (env) => {
    const p = env.payload;
    const nouvelleTx: Transaction = {
      id: p.transaction_id,
      horodatage: p.horodatage_tx || env.horodatage,
      montant: p.montant ?? 0,
      marchand: p.marchand || '—',
      carteFin: p.carte_fin || '????',
      banqueId: p.banque,
      statut: actionVersStatut(p.action),
      probabilite: p.score,
    };

    // Temps de décision = horodatage enveloppe (réception) - horodatage transaction (émission)
    let dureeMs = 0;
    if (p.horodatage_tx) {
      const t0 = Date.parse(p.horodatage_tx);
      const t1 = Date.parse(env.horodatage);
      if (!Number.isNaN(t0) && !Number.isNaN(t1) && t1 >= t0) dureeMs = t1 - t0;
    }

    set((state) => {
      const transactions = [nouvelleTx, ...state.transactions].slice(0, TAILLE_MAX_FLUX);
      const alertes =
        p.action === 'APPROUVER'
          ? state.alertes
          : [p, ...state.alertes].slice(0, TAILLE_MAX_FLUX);

      const fraudes = state.kpi.fraudesDetectees + (p.action !== 'APPROUVER' ? 1 : 0);
      const total = state.kpi.totalTransactions + 1;
      // Moyenne mobile cumulative : ((moy * (n-1)) + nouveau) / n
      const tempsMoyenDecisionMs =
        dureeMs > 0
          ? Math.round((state.kpi.tempsMoyenDecisionMs * (total - 1) + dureeMs) / total)
          : state.kpi.tempsMoyenDecisionMs;

      const agents = state.agents.map((a) =>
        a.nom === 'Banque-BNP' || a.nom === p.banque
          ? {
              ...a,
              statut: 'TRAITEMENT' as const,
              dernierePerformatif: p.action === 'APPROUVER' ? 'REJECT_PROPOSAL' : 'ACCEPT_PROPOSAL',
              dernierMessage: `tx=${p.transaction_id.slice(0, 8)} → ${p.action}`,
              derniereActivite: env.horodatage,
            }
          : a,
      );

      return {
        transactions,
        alertes,
        agents,
        kpi: {
          ...state.kpi,
          totalTransactions: total,
          fraudesDetectees: fraudes,
          tempsMoyenDecisionMs,
        },
      };
    });
  },

  reinitialiser: () =>
    set({
      transactions: [],
      alertes: [],
      agents: AGENTS_INITIAUX,
      kpi: KPI_INITIAL,
    }),
}));
