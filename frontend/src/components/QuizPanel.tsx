import { useState } from 'react';
import { useQuiz } from '../hooks/useQuiz';
import { useSession } from '../hooks/useSession';
import { useProfileMasters } from '../hooks/useProfileMasters';
import Button from './ui/Button';
import Card from './ui/Card';
import { Field, Select, TextArea } from './ui/FormField';
import type { Difficulty } from '../types';

interface Props {
  sessionId: number;
  onActivity?: () => void;
}

const DIFFICULTY_OPTIONS: { value: Difficulty; label: string }[] = [
  { value: 'easy', label: 'Easy' },
  { value: 'medium', label: 'Medium' },
  { value: 'hard', label: 'Advanced' }
];

export default function QuizPanel({ sessionId, onActivity }: Props) {
  const { session } = useSession(sessionId);
  const { masters, error: mastersError } = useProfileMasters();
  const { config, setConfig, questions, activeConfig, results, generating, grading, error, generate, submitAnswer } =
    useQuiz(sessionId, session, onActivity);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [showSetup, setShowSetup] = useState(true);

  const onGenerate = async () => {
    setAnswers({});
    await generate();
    setShowSetup(false);
  };

  const answeredCount = Object.keys(results).length;
  const correctCount = Object.values(results).filter((r) => r.correct).length;

  return (
    <div className="flex h-full flex-col overflow-y-auto px-5 py-4">
      {questions.length === 0 || showSetup ? (
        <Card className="space-y-4">
          <div>
            <h4 className="text-sm font-bold text-white">Quiz setup</h4>
            <p className="mt-1 text-xs text-booki-muted">
              Choose who quizzes you, how hard it is, and how many questions.
            </p>
          </div>

          <Field label="Profile Master">
            <Select
              value={config.profileMasterId ?? ''}
              onChange={(e) =>
                setConfig((prev) => ({
                  ...prev,
                  profileMasterId: e.target.value ? Number(e.target.value) : null
                }))
              }
            >
              <option value="">Default</option>
              {masters.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </Select>
            {mastersError && <p className="mt-1 text-xs text-rose-400">{mastersError}</p>}
          </Field>

          <Field label="Difficulty">
            <div className="grid grid-cols-3 gap-2">
              {DIFFICULTY_OPTIONS.map((d) => (
                <button
                  key={d.value}
                  type="button"
                  onClick={() => setConfig((prev) => ({ ...prev, difficulty: d.value }))}
                  className={`rounded-lg py-2 text-xs font-bold transition ${
                    config.difficulty === d.value
                      ? 'bg-booki-accent text-white'
                      : 'bg-booki-bg/60 text-white/70 hover:bg-booki-card-hover'
                  }`}
                >
                  {d.label}
                </button>
              ))}
            </div>
          </Field>

          <Field label={`Number of questions: ${config.questionCount}`}>
            <input
              type="range"
              min={1}
              max={5}
              value={config.questionCount}
              onChange={(e) => setConfig((prev) => ({ ...prev, questionCount: Number(e.target.value) }))}
              className="w-full accent-booki-accent"
            />
          </Field>

          <Button onClick={onGenerate} disabled={generating} className="w-full">
            {generating ? 'Generating…' : questions.length > 0 ? 'Regenerate quiz' : 'Generate quiz'}
          </Button>
          {error && <p className="text-sm text-rose-400">{error}</p>}
        </Card>
      ) : (
        <div className="space-y-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-booki-muted">
                {answeredCount}/{questions.length} answered · {correctCount} correct
              </p>
              {activeConfig?.masterName && (
                <p className="text-[11px] text-white/40">
                  {activeConfig.masterName} · {activeConfig.difficulty}
                </p>
              )}
            </div>
            <Button variant="ghost" size="sm" onClick={() => setShowSetup(true)}>
              Edit setup
            </Button>
          </div>
          {error && <p className="text-xs text-rose-400">{error}</p>}

          {questions.map((q) => {
            const result = results[q.id];
            return (
              <Card key={q.id}>
                <p className="text-xs font-semibold uppercase tracking-wide text-booki-muted">
                  Page {q.pageNumber}
                </p>
                <p className="mt-1 text-sm font-medium text-white">{q.question}</p>
                <TextArea
                  value={answers[q.id] || ''}
                  onChange={(e) => setAnswers((prev) => ({ ...prev, [q.id]: e.target.value }))}
                  disabled={!!result}
                  rows={2}
                  placeholder="Type your answer…"
                  className="mt-3 bg-booki-bg/60 disabled:opacity-70"
                />
                {!result ? (
                  <Button
                    size="sm"
                    onClick={() => submitAnswer(q, answers[q.id] || '')}
                    disabled={grading === q.id || !answers[q.id]?.trim()}
                    className="mt-2"
                  >
                    {grading === q.id ? 'Checking…' : 'Check answer'}
                  </Button>
                ) : (
                  <p
                    className={`mt-2 text-xs font-medium ${
                      result.correct ? 'text-emerald-400' : 'text-amber-400'
                    }`}
                  >
                    {result.correct ? '✓ ' : '✗ '}
                    {result.feedback}
                  </p>
                )}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
