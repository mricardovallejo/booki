import { useCallback, useEffect, useState } from 'react';
import { getSession, updateCurrentPage } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
import type { Session } from '../types';

export function useSession(sessionId: number) {
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(
    () =>
      getSession(sessionId)
        .then((result) => {
          setSession(result);
          setError(null);
        })
        .catch((err) => setError(getErrorMessage(err, 'Could not load this session.'))),
    [sessionId]
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  const goToPage = useCallback(
    async (page: number) => {
      if (!session) return;
      try {
        const updated = await updateCurrentPage(sessionId, page);
        setSession(updated);
        setError(null);
      } catch (err) {
        setError(getErrorMessage(err, 'Could not update your page.'));
      }
    },
    [session, sessionId]
  );

  return { session, error, goToPage, refresh };
}
