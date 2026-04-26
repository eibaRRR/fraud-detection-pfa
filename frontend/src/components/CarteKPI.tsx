import type { ReactNode } from 'react';
import { useStore } from '../store/useStore';

interface KpiCardProps {
  titre: string;
  valeur: string | number;
  sousTitre?: string;
  icone?: ReactNode;
  variante?: 'ok' | 'warn' | 'danger' | 'neutral';
}

const VARIANTE_COULEUR: Record<NonNullable<KpiCardProps['variante']>, string> = {
  ok:      'border-emerald-500/30 text-emerald-300',
  warn:    'border-amber-500/30   text-amber-300',
  danger:  'border-red-500/30     text-red-300',
  neutral: 'border-border-strong  text-slate-100',
};

const KpiCard = ({ titre, valeur, sousTitre, icone, variante = 'neutral' }: KpiCardProps) => (
  <div className={`panneau p-5 border ${VARIANTE_COULEUR[variante]}`}>
    <div className="flex items-start justify-between">
      <div>
        <p className="text-xs uppercase tracking-wider text-slate-400">{titre}</p>
        <p className="font-mono text-3xl font-bold mt-2 text-slate-50">{valeur}</p>
        {sousTitre && <p className="text-xs text-slate-500 mt-1">{sousTitre}</p>}
      </div>
      {icone && <div className="text-2xl opacity-60">{icone}</div>}
    </div>
  </div>
);

/**
 * Bandeau de 4 cartes KPI affichant :
 * total transactions, fraudes détectées, taux de fraude, temps moyen.
 * Mises à jour en temps réel via le store global.
 */
export const CarteKPI = (): JSX.Element => {
  const kpi = useStore((s) => s.kpi);
  const tauxFraude = kpi.totalTransactions === 0
    ? 0
    : (kpi.fraudesDetectees / kpi.totalTransactions) * 100;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <KpiCard
        titre="Total transactions"
        valeur={kpi.totalTransactions.toLocaleString('fr-FR')}
        sousTitre="depuis le début de la session"
        icone="📊"
      />
      <KpiCard
        titre="Fraudes détectées"
        valeur={kpi.fraudesDetectees.toLocaleString('fr-FR')}
        sousTitre={`${tauxFraude.toFixed(2)} % du flux`}
        variante="danger"
        icone="🚨"
      />
      <KpiCard
        titre="Taux faux positifs"
        valeur={`${(kpi.tauxFauxPositifs * 100).toFixed(2)} %`}
        sousTitre="estimation glissante"
        variante="warn"
        icone="⚖️"
      />
      <KpiCard
        titre="Temps moyen décision"
        valeur={`${kpi.tempsMoyenDecisionMs} ms`}
        sousTitre="Surveillance → Banque"
        variante="ok"
        icone="⚡"
      />
    </div>
  );
};
