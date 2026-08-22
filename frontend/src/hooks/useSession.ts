import { useCallback, useEffect, useState } from 'react';
import { getSession, updateCurrentPage } from '../api/sessions';
import type { Session } from '../types';

export function useSession(sessionId: number) {
  const [session, setSession] = useState<Session | null>(null);

  const refresh = useCallback(() => getSession(sessionId).then(setSession), [sessionId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const goToPage = useCallback(
    async (page: number) => {
      if (!session) return;
      const clamped = Math.max(session.startPage, Math.min(page, session.endPage));
      const updated = await updateCurrentPage(sessionId, clamped);
      setSession(updated);
    },
    [session, sessionId]
  );

  return { session, goToPage, refresh };
}
