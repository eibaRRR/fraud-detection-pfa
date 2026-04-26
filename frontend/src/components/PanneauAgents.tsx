import { useStore } from '../store/useStore';
import type { StatutAgent } from '../types';

const COULEUR_STATUT: Record<StatutAgent, string> = {
  ACTIF:       'bg-emerald-500',
  TRAITEMENT:  'bg-amber-500 animate-pulse-soft',
  INACTIF:     'bg-slate-600',
};

const LIBELLE_STATUT: Record<StatutAgent, string> = {
  ACTIF:       'Actif',
  TRAITEMENT:  'En traitement',
  INACTIF:     'Inactif',
};

/**
 * Liste les 6 agents JADE avec leur état et leur dernier message ACL.
 * Un point coloré (animé pour TRAITEMENT) indique l'état en temps réel.
 */
export const PanneauAgents = (): JSX.Element => {
  const agents = useStore((s) => s.agents);

  return (
    <div className="panneau p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold">Agents JADE</h2>
        <span className="text-xs text-slate-400">{agents.length} agents</span>
      </div>

      <ul className="space-y-3">
        {agents.map((a) => (
          <li
            key={a.nom}
            className="flex items-center justify-between bg-bg-elev px-4 py-3 rounded-lg border border-border/60"
          >
            <div className="flex items-center gap-3">
              <span
                className={`inline-block w-2.5 h-2.5 rounded-full ${COULEUR_STATUT[a.statut]}`}
                title={LIBELLE_STATUT[a.statut]}
              />
              <div>
                <p className="font-mono text-sm font-semibold">{a.nom}</p>
                <p className="text-xs text-slate-400">{a.role}</p>
              </div>
            </div>
            <div className="text-right">
              {a.dernierePerformatif && (
                <p className="badge badge-neutral">{a.dernierePerformatif}</p>
              )}
              {a.dernierMessage && (
                <p className="text-xs text-slate-400 mt-1 font-mono truncate max-w-[260px]">
                  {a.dernierMessage}
                </p>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
};
