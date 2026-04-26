import { useState } from 'react';
import { useStore } from '../store/useStore';
import type { StatutTransaction, Transaction } from '../types';
import { ModalDetailAlerte } from './ModalDetailAlerte';

const BADGE: Record<StatutTransaction, string> = {
  EN_ATTENTE: 'badge badge-neutral',
  APPROUVE:   'badge badge-ok',
  SIGNALE:    'badge badge-warn',
  BLOQUE:     'badge badge-danger',
};

const COULEUR_PROBA = (p: number): string => {
  if (p >= 0.75) return 'bg-red-500';
  if (p >= 0.5) return 'bg-amber-500';
  return 'bg-emerald-500';
};

const TAILLE_PAGE = 20;

/**
 * Tableau paginé des dernières transactions reçues via WebSocket,
 * avec badge de statut, barre de progression de la probabilité,
 * et ouverture d'une modale détail au clic.
 */
export const FluxTransactions = (): JSX.Element => {
  const transactions = useStore((s) => s.transactions);
  const alertes = useStore((s) => s.alertes);
  const [page, setPage] = useState(0);
  const [selection, setSelection] = useState<Transaction | null>(null);

  const debut = page * TAILLE_PAGE;
  const visibles = transactions.slice(debut, debut + TAILLE_PAGE);
  const totalPages = Math.max(1, Math.ceil(transactions.length / TAILLE_PAGE));

  const detailDe = (txId: string) => alertes.find((a) => a.transaction_id === txId) ?? null;

  return (
    <div className="panneau p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold">Flux des transactions</h2>
        <span className="text-xs text-slate-400">{transactions.length} reçues</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-slate-400 text-xs uppercase tracking-wider">
            <tr className="border-b border-border">
              <th className="text-left py-2 font-medium">ID</th>
              <th className="text-left py-2 font-medium">Heure</th>
              <th className="text-left py-2 font-medium">Banque</th>
              <th className="text-left py-2 font-medium">Marchand</th>
              <th className="text-right py-2 font-medium">Montant</th>
              <th className="text-left py-2 font-medium">Carte</th>
              <th className="text-left py-2 font-medium">Statut</th>
              <th className="text-left py-2 font-medium w-48">Probabilité</th>
            </tr>
          </thead>
          <tbody>
            {visibles.length === 0 && (
              <tr>
                <td colSpan={8} className="py-10 text-center text-slate-500">
                  En attente de transactions...
                </td>
              </tr>
            )}
            {visibles.map((tx) => (
              <tr
                key={tx.id}
                onClick={() => setSelection(tx)}
                className="border-b border-border/40 hover:bg-bg-elev cursor-pointer transition"
              >
                <td className="py-2 font-mono text-xs text-slate-400">
                  {tx.id.slice(0, 8)}
                </td>
                <td className="py-2 text-slate-300">
                  {new Date(tx.horodatage).toLocaleTimeString('fr-FR')}
                </td>
                <td className="py-2 text-slate-300">{tx.banqueId}</td>
                <td className="py-2 text-slate-300">{tx.marchand}</td>
                <td className="py-2 text-right font-mono">
                  {tx.montant.toFixed(2)} €
                </td>
                <td className="py-2 font-mono text-slate-400">**** {tx.carteFin}</td>
                <td className="py-2">
                  <span className={BADGE[tx.statut]}>{tx.statut}</span>
                </td>
                <td className="py-2">
                  <div className="flex items-center gap-2">
                    <div className="flex-1 h-1.5 bg-bg-elev rounded">
                      <div
                        className={`h-1.5 rounded ${COULEUR_PROBA(tx.probabilite)}`}
                        style={{ width: `${(tx.probabilite * 100).toFixed(0)}%` }}
                      />
                    </div>
                    <span className="font-mono text-xs w-12 text-right">
                      {(tx.probabilite * 100).toFixed(1)}%
                    </span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between mt-4 text-sm">
        <button
          onClick={() => setPage((p) => Math.max(0, p - 1))}
          disabled={page === 0}
          className="px-3 py-1 rounded bg-bg-elev disabled:opacity-30"
        >
          ← Précédent
        </button>
        <span className="text-slate-400">
          Page {page + 1} / {totalPages}
        </span>
        <button
          onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
          disabled={page >= totalPages - 1}
          className="px-3 py-1 rounded bg-bg-elev disabled:opacity-30"
        >
          Suivant →
        </button>
      </div>

      {selection && (
        <ModalDetailAlerte
          transaction={selection}
          decision={detailDe(selection.id)}
          onClose={() => setSelection(null)}
        />
      )}
    </div>
  );
};
