import { useEffect, useState } from 'react';
import { getProgress } from '../api/sessions';
import type { SessionProgress } from '../types';

export function useProgress(sessionId: number, refreshKey: number) {
  const [progress, setProgress] = useState<SessionProgress | null>(null);

  useEffect(() => {
    getProgress(sessionId).then(setProgress);
  }, [sessionId, refreshKey]);

  return progress;
}
