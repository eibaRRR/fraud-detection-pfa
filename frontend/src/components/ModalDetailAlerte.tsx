import type { DecisionPayload, Transaction } from '../types';

interface Props {
  transaction: Transaction;
  decision: DecisionPayload | null;
  onClose: () => void;
}

const ETAPES = [
  { agent: 'Surveillance', message: 'Réception et validation du flux' },
  { agent: 'AnalyseML',    message: 'Scoring XGBoost + Isolation Forest' },
  { agent: 'Risque',       message: 'Application des règles métier (R1/R2/R3)' },
  { agent: 'Banque',       message: 'Décision finale (FIPA-ContractNet)' },
];

/**
 * Modale détaillant la chaîne de décision multi-agents pour une transaction.
 *
 * Affiche en timeline les 4 étapes (Surveillance → ML → Risque → Banque),
 * le score ML, les règles déclenchées, et la décision finale + raisonnement.
 */
export const ModalDetailAlerte = ({ transaction, decision, onClose }: Props): JSX.Element => {
  return (
    <div
      className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="panneau w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-lg font-semibold">Chaîne de décision</h3>
            <p className="font-mono text-xs text-slate-400 mt-1">
              tx={transaction.id}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-200 text-xl"
            aria-label="Fermer"
          >
            ✕
          </button>
        </div>

        {/* Bandeau récap */}
        <div className="grid grid-cols-3 gap-3 mb-6">
          <div className="bg-bg-elev p-3 rounded">
            <p className="text-xs text-slate-400">Score final</p>
            <p className="font-mono text-2xl font-bold">
              {(transaction.probabilite * 100).toFixed(1)}%
            </p>
          </div>
          <div className="bg-bg-elev p-3 rounded">
            <p className="text-xs text-slate-400">Statut</p>
            <p className="font-mono text-lg">{transaction.statut}</p>
          </div>
          <div className="bg-bg-elev p-3 rounded">
            <p className="text-xs text-slate-400">Banque</p>
            <p className="font-mono text-lg">{decision?.banque ?? transaction.banqueId}</p>
          </div>
        </div>

        {/* Timeline des agents */}
        <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-400 mb-3">
          Timeline ACL
        </h4>
        <ol className="border-l-2 border-border-strong space-y-4 pl-5">
          {ETAPES.map((e, idx) => (
            <li key={e.agent} className="relative">
              <span className="absolute -left-[27px] w-4 h-4 rounded-full bg-accent border-2 border-bg" />
              <p className="font-mono text-sm font-semibold">
                {idx + 1}. {e.agent}
              </p>
              <p className="text-xs text-slate-400">{e.message}</p>
            </li>
          ))}
        </ol>

        {/* Détails décision */}
        {decision && (
          <div className="mt-6 space-y-3">
            <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              Décision finale
            </h4>
            <div className="bg-bg-elev p-4 rounded">
              <p className="text-sm">
                <span className="text-slate-400">Action :</span>{' '}
                <span className="font-mono font-semibold">{decision.action}</span>
              </p>
              <p className="text-sm mt-2">
                <span className="text-slate-400">Raisonnement :</span>
              </p>
              <pre className="font-mono text-xs text-slate-300 whitespace-pre-wrap mt-1">
                {decision.raisonnement}
              </pre>
              {decision.regles_declenchees.length > 0 && (
                <div className="mt-3">
                  <p className="text-xs text-slate-400 mb-1">Règles déclenchées :</p>
                  <div className="flex flex-wrap gap-2">
                    {decision.regles_declenchees.map((r) => (
                      <span key={r} className="badge badge-warn">{r}</span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
