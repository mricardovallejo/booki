import { useEffect, useState } from 'react';
import { getQuizReport } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
import type { QuizReport } from '../types';

export function useQuizReport(sessionId: number, refreshKey: number) {
  const [report, setReport] = useState<QuizReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getQuizReport(sessionId)
      .then((result) => {
        setReport(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load the quiz report.')));
  }, [sessionId, refreshKey]);

  return { report, error };
}
