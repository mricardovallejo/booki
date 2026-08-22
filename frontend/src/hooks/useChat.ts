import { useCallback, useEffect, useState } from 'react';
import { listMessages, sendMessage } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
import type { Message } from '../types';

export function useChat(sessionId: number, onActivity?: () => void) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(
    () =>
      listMessages(sessionId)
        .then((result) => {
          setMessages(result);
          setError(null);
        })
        .catch((err) => setError(getErrorMessage(err, 'Could not load this conversation.'))),
    [sessionId]
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  const send = useCallback(
    async (text: string, inputType: 'TEXT' | 'VOICE' = 'TEXT') => {
      if (!text.trim()) return;
      setSending(true);
      setError(null);
      try {
        await sendMessage(sessionId, text.trim(), inputType);
        await refresh();
        onActivity?.();
      } catch (err) {
        setError(getErrorMessage(err, 'BooKI could not reply. Try again.'));
      } finally {
        setSending(false);
      }
    },
    [sessionId, refresh, onActivity]
  );

  return { messages, sending, error, send, refresh };
}
