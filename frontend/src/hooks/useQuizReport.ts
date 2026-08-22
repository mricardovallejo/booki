import { useEffect, useState } from 'react';
import { getQuizReport } from '../api/sessions';
import type { QuizReport } from '../types';

export function useQuizReport(sessionId: number, refreshKey: number) {
  const [report, setReport] = useState<QuizReport | null>(null);

  useEffect(() => {
    getQuizReport(sessionId).then(setReport);
  }, [sessionId, refreshKey]);

  return report;
}
