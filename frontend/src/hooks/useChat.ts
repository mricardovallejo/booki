import { useCallback, useEffect, useState } from 'react';
import { listMessages, sendMessage } from '../api/sessions';
import { sendVoiceTurn, type VoiceTurnResult } from '../api/voice';
import { getErrorMessage } from '../lib/errors';
import type { CapabilityHint, Message } from '../types';

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
    async (
      text: string,
      inputType: 'TEXT' | 'VOICE' = 'TEXT',
      capabilityHint?: CapabilityHint
    ) => {
      if (!text.trim()) return;
      setSending(true);
      setError(null);
      try {
        await sendMessage(sessionId, text.trim(), inputType, capabilityHint);
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

  // Cloud voice path: upload the recorded clip; the backend transcribes, runs
  // the same conversation pipeline, and returns the persisted messages plus an
  // optional spoken reply for the caller to play.
  const sendVoice = useCallback(
    async (
      audio: Blob,
      capabilityHint?: CapabilityHint,
      wantsAudioReply = true
    ): Promise<VoiceTurnResult | null> => {
      setSending(true);
      setError(null);
      try {
        const result = await sendVoiceTurn(sessionId, audio, capabilityHint, wantsAudioReply);
        await refresh();
        onActivity?.();
        return result;
      } catch (err) {
        setError(getErrorMessage(err, 'BooKI could not hear you. Try again.'));
        return null;
      } finally {
        setSending(false);
      }
    },
    [sessionId, refresh, onActivity]
  );

  return { messages, sending, error, send, sendVoice, refresh };
}
