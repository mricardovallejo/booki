import { useEffect, useState } from 'react';
import { getProgress } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
import type { SessionProgress } from '../types';

export function useProgress(sessionId: number, refreshKey: number) {
  const [progress, setProgress] = useState<SessionProgress | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getProgress(sessionId)
      .then((result) => {
        setProgress(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load your progress.')));
  }, [sessionId, refreshKey]);

  return { progress, error };
}
