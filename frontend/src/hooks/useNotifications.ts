import { useEffect, useState } from 'react';
import { getNotifications } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
import type { SessionNotification } from '../types';

export function useNotifications(sessionId: number, refreshKey: number) {
  const [notifications, setNotifications] = useState<SessionNotification[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getNotifications(sessionId)
      .then((result) => {
        setNotifications(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load notifications.')));
  }, [sessionId, refreshKey]);

  return { notifications, error };
}
