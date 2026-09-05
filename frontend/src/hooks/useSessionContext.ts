import { useEffect, useState } from 'react';
import { getSessionContext } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
import type { SessionContext } from '../types';

/**
 * Loads the layered instruction context shown in the "what BooKI reads" panel.
 * Exposes `loading` / `error` so callers can tell "still loading" apart from
 * "failed" instead of silently rendering nothing.
 */
export function useSessionContext(sessionId: number) {
  const [context, setContext] = useState<SessionContext | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getSessionContext(sessionId)
      .then((result) => {
        if (!cancelled) setContext(result);
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, 'Could not load the session context.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  return { context, loading, error };
}
