import { useCallback, useEffect, useState } from 'react';
import { generateQuiz, getQuizReport, submitQuizAnswer } from '../api/sessions';
import { getErrorMessage } from '../lib/errors';
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
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (session) {
      setConfig((prev) => ({
        ...prev,
        profileMasterId: session.profileMasterId ?? null,
        difficulty: session.difficulty
      }));
    }
  }, [session]);

  const loadReport = useCallback(
    () =>
      getQuizReport(sessionId)
        .then((result) => {
          setReport(result);
          setError(null);
        })
        .catch((err) => setError(getErrorMessage(err, 'Could not load the quiz report.'))),
    [sessionId]
  );

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  const generate = useCallback(async () => {
    setGenerating(true);
    setError(null);
    setResults({});
    try {
      const result = await generateQuiz(sessionId, config);
      setQuestions(result.questions);
      setActiveConfig(result.config);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not generate a quiz.'));
    } finally {
      setGenerating(false);
    }
  }, [sessionId, config]);

  const submitAnswer = useCallback(
    async (question: QuizQuestion, answer: string) => {
      setGrading(question.id);
      setError(null);
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
        loadReport();
        return result;
      } catch (err) {
        setError(getErrorMessage(err, 'Could not check this answer.'));
        return null;
      } finally {
        setGrading(null);
      }
    },
    [sessionId, activeConfig, config, onActivity, loadReport]
  );

  return {
    config,
    setConfig,
    questions,
    activeConfig,
    results,
    generating,
    grading,
    error,
    generate,
    submitAnswer,
    report,
    loadReport
  };
}
