import { useCallback } from 'react';
import { CarteKPI } from './components/CarteKPI';
import { FluxTransactions } from './components/FluxTransactions';
import { GraphiqueFraude } from './components/GraphiqueFraude';
import { PanneauAgents } from './components/PanneauAgents';
import { useWebSocket } from './hooks/useWebSocket';
import { useStore } from './store/useStore';
import type { AlerteEnveloppe } from './types';

const ETIQUETTE_CONNEXION = {
  CONNEXION: { texte: 'Connexion...', classe: 'badge badge-warn' },
  OUVERTE:   { texte: 'Connecté',     classe: 'badge badge-ok'   },
  FERMEE:    { texte: 'Déconnecté',   classe: 'badge badge-danger' },
} as const;

const App = (): JSX.Element => {
  const ajouterAlerte = useStore((s) => s.ajouterAlerte);

  const handleAlerte = useCallback(
    (env: AlerteEnveloppe) => ajouterAlerte(env),
    [ajouterAlerte],
  );

  const { etat } = useWebSocket(handleAlerte);
  const meta = ETIQUETTE_CONNEXION[etat];

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-border bg-bg-panel">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold tracking-tight">
              PFA · Détection de fraude bancaire
            </h1>
            <p className="text-xs text-slate-400 mt-0.5">
              Système Multi-Agents JADE × Pipeline ML
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className={meta.classe}>{meta.texte}</span>
            <span className="text-xs text-slate-500 font-mono">
              ws://localhost:8080/alertes
            </span>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-6 space-y-6">
        <CarteKPI />

        <GraphiqueFraude />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2">
            <FluxTransactions />
          </div>
          <div>
            <PanneauAgents />
          </div>
        </div>
      </main>

      <footer className="border-t border-border bg-bg-panel">
        <div className="max-w-7xl mx-auto px-6 py-3 text-xs text-slate-500 flex justify-between">
          <span>Projet de Fin d'Année · Équipe Fraud-Detection</span>
          <span className="font-mono">v1.0.0</span>
        </div>
      </footer>
    </div>
  );
};

export default App;
