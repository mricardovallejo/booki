import { useEffect, useState } from 'react';
import { getNotifications } from '../api/sessions';
import type { SessionNotification } from '../types';

export function useNotifications(sessionId: number, refreshKey: number) {
  const [notifications, setNotifications] = useState<SessionNotification[]>([]);

  useEffect(() => {
    getNotifications(sessionId).then(setNotifications).catch(() => setNotifications([]));
  }, [sessionId, refreshKey]);

  return notifications;
}
