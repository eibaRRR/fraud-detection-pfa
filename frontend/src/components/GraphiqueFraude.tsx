import { useMemo } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useStore } from '../store/useStore';

interface PointHoraire {
  heure: string;
  fraudes: number;
  total: number;
  taux: number;
}

interface PointBanque {
  banque: string;
  fraudes: number;
}

const NB_HEURES = 24;

/**
 * Deux graphiques Recharts :
 * - LineChart du taux de fraude par heure sur les 24 dernières heures
 * - BarChart du nombre de fraudes par banque simulée
 */
export const GraphiqueFraude = (): JSX.Element => {
  const transactions = useStore((s) => s.transactions);
  const alertes = useStore((s) => s.alertes);

  const donneesHoraires = useMemo<PointHoraire[]>(() => {
    const buckets: Record<number, { fraudes: number; total: number }> = {};
    for (let h = 0; h < NB_HEURES; h++) buckets[h] = { fraudes: 0, total: 0 };
    for (const tx of transactions) {
      const h = new Date(tx.horodatage).getHours();
      buckets[h].total += 1;
      if (tx.statut === 'BLOQUE' || tx.statut === 'SIGNALE') {
        buckets[h].fraudes += 1;
      }
    }
    return Object.entries(buckets).map(([h, v]) => ({
      heure: `${h.padStart(2, '0')}h`,
      fraudes: v.fraudes,
      total: v.total,
      taux: v.total === 0 ? 0 : Math.round((v.fraudes / v.total) * 100),
    }));
  }, [transactions]);

  const donneesParBanque = useMemo<PointBanque[]>(() => {
    const acc: Record<string, number> = {};
    for (const a of alertes) {
      acc[a.banque] = (acc[a.banque] ?? 0) + 1;
    }
    return Object.entries(acc).map(([banque, fraudes]) => ({ banque, fraudes }));
  }, [alertes]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="panneau p-5">
        <h3 className="text-sm font-semibold mb-4 text-slate-300">
          Taux de fraude / heure (24h)
        </h3>
        <ResponsiveContainer width="100%" height={240}>
          <LineChart data={donneesHoraires}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
            <XAxis dataKey="heure" stroke="#94a3b8" fontSize={11} />
            <YAxis stroke="#94a3b8" fontSize={11} unit="%" />
            <Tooltip
              contentStyle={{ backgroundColor: '#121826', border: '1px solid #1f2937' }}
              labelStyle={{ color: '#cbd5e1' }}
            />
            <Legend />
            <Line
              type="monotone"
              dataKey="taux"
              name="Taux de fraude"
              stroke="#ef4444"
              strokeWidth={2}
              dot={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="panneau p-5">
        <h3 className="text-sm font-semibold mb-4 text-slate-300">
          Fraudes par banque
        </h3>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={donneesParBanque}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
            <XAxis dataKey="banque" stroke="#94a3b8" fontSize={11} />
            <YAxis stroke="#94a3b8" fontSize={11} />
            <Tooltip
              contentStyle={{ backgroundColor: '#121826', border: '1px solid #1f2937' }}
              labelStyle={{ color: '#cbd5e1' }}
            />
            <Bar dataKey="fraudes" fill="#3b82f6" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
