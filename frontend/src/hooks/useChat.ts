import { useCallback, useEffect, useState } from 'react';
import { listMessages, sendMessage } from '../api/sessions';
import type { Message } from '../types';

export function useChat(sessionId: number, onActivity?: () => void) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [sending, setSending] = useState(false);

  const refresh = useCallback(() => listMessages(sessionId).then(setMessages), [sessionId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const send = useCallback(
    async (text: string, inputType: 'TEXT' | 'VOICE' = 'TEXT') => {
      if (!text.trim()) return;
      setSending(true);
      try {
        await sendMessage(sessionId, text.trim(), inputType);
        await refresh();
        onActivity?.();
      } finally {
        setSending(false);
      }
    },
    [sessionId, refresh, onActivity]
  );

  return { messages, sending, send, refresh };
}
