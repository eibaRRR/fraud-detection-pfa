/**
 * Types partagés du dashboard.
 * Reflètent strictement le contrat ACL/DB du backend Java.
 */

export type ActionDecision = 'BLOQUER' | 'SIGNALER' | 'APPROUVER';

export type StatutTransaction =
  | 'EN_ATTENTE'
  | 'APPROUVE'
  | 'SIGNALE'
  | 'BLOQUE';

/** Une transaction telle qu'affichée dans le flux. */
export interface Transaction {
  id: string;
  horodatage: string; // ISO-8601
  montant: number;
  marchand: string;
  carteFin: string;
  banqueId: string;
  statut: StatutTransaction;
  probabilite: number; // 0..1
}

/** Une décision diffusée par le WebSocket (payload de AlerteService.diffuserWebSocket). */
export interface DecisionPayload {
  transaction_id: string;
  score: number;
  action: ActionDecision;
  banque: string;
  raisonnement: string;
  regles_declenchees: string[];
  montant?: number;
  marchand?: string;
  carte_fin?: string;
  horodatage_tx?: string;
}

/** Enveloppe WebSocket émise par AlerteService.diffuserWebSocket. */
export interface AlerteEnveloppe {
  type: 'ALERTE';
  horodatage: string;
  payload: DecisionPayload;
}

/** Statut d'un agent JADE pour le panneau d'agents. */
export type StatutAgent = 'ACTIF' | 'INACTIF' | 'TRAITEMENT';

export interface EtatAgent {
  nom: string;
  role: string;
  statut: StatutAgent;
  dernierMessage?: string;
  dernierePerformatif?: string;
  derniereActivite?: string; // ISO-8601
}

/** KPI agrégés affichés dans les cartes. */
export interface KpiSysteme {
  totalTransactions: number;
  fraudesDetectees: number;
  tauxFauxPositifs: number; // 0..1
  tempsMoyenDecisionMs: number;
}
