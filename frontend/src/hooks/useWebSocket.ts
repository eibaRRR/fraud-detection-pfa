import { useEffect, useRef, useState } from 'react';
import type { AlerteEnveloppe } from '../types';

const URL_DEFAUT =
  (import.meta.env.VITE_WS_URL as string | undefined) ?? 'ws://localhost:8080/alertes';

const BACKOFF_INITIAL_MS = 1_000;
const BACKOFF_MAX_MS = 30_000;

/** État de la connexion WebSocket exposé aux composants. */
export type EtatConnexion = 'CONNEXION' | 'OUVERTE' | 'FERMEE';

/**
 * Hook React encapsulant la connexion WebSocket vers ws://localhost:8080/alertes.
 *
 * - Reconnexion automatique avec backoff exponentiel (1s -> 30s max)
 * - Parsing strict du JSON et filtrage par `type === 'ALERTE'`
 * - Callback invoqué à chaque message valide
 *
 * @param onAlerte callback appelé pour chaque alerte reçue
 * @param url      URL du WS (défaut : variable d'env VITE_WS_URL)
 */
export function useWebSocket(
  onAlerte: (alerte: AlerteEnveloppe) => void,
  url: string = URL_DEFAUT,
): { etat: EtatConnexion } {
  const [etat, setEtat] = useState<EtatConnexion>('CONNEXION');
  const wsRef = useRef<WebSocket | null>(null);
  const backoffRef = useRef<number>(BACKOFF_INITIAL_MS);
  const timerRef = useRef<number | null>(null);
  const callbackRef = useRef(onAlerte);

  useEffect(() => {
    callbackRef.current = onAlerte;
  }, [onAlerte]);

  useEffect(() => {
    let stopped = false;

    const connecter = (): void => {
      if (stopped) return;
      setEtat('CONNEXION');
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        backoffRef.current = BACKOFF_INITIAL_MS;
        setEtat('OUVERTE');
      };

      ws.onmessage = (evt: MessageEvent<string>) => {
        try {
          const data = JSON.parse(evt.data) as AlerteEnveloppe;
          if (data?.type === 'ALERTE' && data.payload) {
            callbackRef.current(data);
          }
        } catch (err) {
          // eslint-disable-next-line no-console
          console.warn('[WS] Message invalide ignoré', err);
        }
      };

      ws.onclose = () => {
        setEtat('FERMEE');
        if (stopped) return;
        const delay = backoffRef.current;
        backoffRef.current = Math.min(delay * 2, BACKOFF_MAX_MS);
        timerRef.current = window.setTimeout(connecter, delay);
      };

      ws.onerror = () => {
        ws.close();
      };
    };

    connecter();

    return () => {
      stopped = true;
      if (timerRef.current !== null) window.clearTimeout(timerRef.current);
      wsRef.current?.close();
    };
  }, [url]);

  return { etat };
}
