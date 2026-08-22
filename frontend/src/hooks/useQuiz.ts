import { useCallback, useEffect, useState } from 'react';
import { generateQuiz, getQuizReport, submitQuizAnswer } from '../api/sessions';
import type { Difficulty, QuizAnswerResult, QuizConfig, QuizQuestion, QuizReport, Session } from '../types';

export interface QuizConfigInput {
  profileMasterId: number | null;
  difficulty: Difficulty;
  questionCount: number;
}

export function useQuiz(sessionId: number, session: Session | null, onActivity?: () => void) {
  const [config, setConfig] = useState<QuizConfigInput>({
    profileMasterId: null,
    difficulty: 'medium',
    questionCount: 3
  });
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [activeConfig, setActiveConfig] = useState<QuizConfig | null>(null);
  const [results, setResults] = useState<Record<number, QuizAnswerResult>>({});
  const [generating, setGenerating] = useState(false);
  const [grading, setGrading] = useState<number | null>(null);
  const [report, setReport] = useState<QuizReport | null>(null);

  useEffect(() => {
    if (session) {
      setConfig((prev) => ({
        ...prev,
        profileMasterId: session.profileMasterId ?? null,
        difficulty: session.difficulty
      }));
    }
  }, [session]);

  const generate = useCallback(async () => {
    setGenerating(true);
    setResults({});
    try {
      const result = await generateQuiz(sessionId, config);
      setQuestions(result.questions);
      setActiveConfig(result.config);
    } finally {
      setGenerating(false);
    }
  }, [sessionId, config]);

  const submitAnswer = useCallback(
    async (question: QuizQuestion, answer: string) => {
      setGrading(question.id);
      try {
        const result = await submitQuizAnswer(sessionId, {
          pageNumber: question.pageNumber,
          question: question.question,
          answer,
          difficulty: activeConfig?.difficulty || config.difficulty,
          profileMasterId: activeConfig?.profileMasterId ?? config.profileMasterId
        });
        setResults((prev) => ({ ...prev, [question.id]: result }));
        onActivity?.();
        return result;
      } finally {
        setGrading(null);
      }
    },
    [sessionId, activeConfig, config, onActivity]
  );

  const loadReport = useCallback(() => getQuizReport(sessionId).then(setReport), [sessionId]);

  return {
    config,
    setConfig,
    questions,
    activeConfig,
    results,
    generating,
    grading,
    generate,
    submitAnswer,
    report,
    loadReport
  };
}
