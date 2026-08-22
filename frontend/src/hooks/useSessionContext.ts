import { useEffect, useState } from 'react';
import { getSessionContext } from '../api/sessions';
import type { SessionContext } from '../types';

export function useSessionContext(sessionId: number) {
  const [context, setContext] = useState<SessionContext | null>(null);

  useEffect(() => {
    getSessionContext(sessionId).then(setContext);
  }, [sessionId]);

  return context;
}
