import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuiz } from '../hooks/useQuiz';
import { useSession } from '../hooks/useSession';
import { useAiProfileSlots } from '../hooks/useAiProfileSlots';
import { useSessionReports } from '../hooks/useSessionReports';
import { ROUTES } from '../config/routes';
import Button from './ui/Button';
import Card from './ui/Card';
import { Field, TextArea, Input } from './ui/FormField';
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
  const profileSlots = useAiProfileSlots(session?.aiProfileId ?? undefined);
  const {
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
    report
  } = useQuiz(sessionId, session, onActivity);
  const { sending, lastSent, error: reportError, send, download } = useSessionReports(sessionId);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [showSetup, setShowSetup] = useState(true);
  const [autoSend, setAutoSend] = useState(false);
  const [reportEmail, setReportEmail] = useState('');
  const autoSentRef = useRef(false);

  const onGenerate = async () => {
    setAnswers({});
    autoSentRef.current = false;
    await generate();
    setShowSetup(false);
  };

  const answeredCount = Object.keys(results).length;
  const correctCount = Object.values(results).filter((r) => r.correct).length;
  const roundComplete = questions.length > 0 && answeredCount === questions.length;

  useEffect(() => {
    if (roundComplete && autoSend && reportEmail.trim() && !autoSentRef.current) {
      autoSentRef.current = true;
      send('quiz', reportEmail.trim());
    }
  }, [roundComplete, autoSend, reportEmail, send]);

  return (
    <div className="flex h-full flex-col overflow-y-auto px-5 py-4">
      {questions.length === 0 || showSetup ? (
        <Card className="space-y-4">
          <div>
            <h4 className="text-sm font-bold text-white">Quiz setup</h4>
            <p className="mt-1 text-xs text-booki-muted">
              Pick how hard it is and how many questions. BooKI uses this session's AI Profile.
            </p>
          </div>

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
            {(() => {
              const rubric = profileSlots.find((s) => s.key === `rubric_${config.difficulty}`)?.content;
              if (!rubric) return null;
              return (
                <div className="mt-2 rounded-lg bg-booki-bg/60 p-2.5 text-[11px] leading-relaxed text-white/60">
                  <p>{rubric}</p>
                  {session?.aiProfileId && (
                    <Link
                      to={`${ROUTES.aiProfile(session.aiProfileId)}?slot=rubric_${config.difficulty}`}
                      className="mt-1 inline-block font-medium text-booki-accent hover:underline"
                    >
                      Fine-tune this level in the AI Profile →
                    </Link>
                  )}
                </div>
              );
            })()}
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

          <div className="rounded-lg bg-booki-bg/60 p-3">
            <label className="flex items-center gap-2 text-xs text-white/80">
              <input
                type="checkbox"
                checked={autoSend}
                onChange={(e) => setAutoSend(e.target.checked)}
                className="rounded border-white/20 bg-booki-card"
              />
              Email me a copy of the correction report when I finish this quiz
            </label>
            {autoSend && (
              <Input
                type="email"
                value={reportEmail}
                onChange={(e) => setReportEmail(e.target.value)}
                placeholder="parent@email.com"
                className="mt-2"
              />
            )}
          </div>

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
              {activeConfig?.profileName && (
                <p className="text-[11px] text-white/40">
                  {activeConfig.profileName} · {activeConfig.difficulty}
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
                    {result.correct ? '✓' : '✗'} {Math.round(result.score * 100)}% — {result.feedback}
                  </p>
                )}
              </Card>
            );
          })}

          {report && report.attempts.length > 0 && (
            <div className="space-y-3 border-t border-white/10 pt-5">
              <p className="text-xs font-semibold uppercase tracking-wide text-booki-muted">
                Correction report — this session
              </p>

              <div className="grid grid-cols-3 gap-3">
                <Card className="text-center">
                  <p className="text-xl font-bold text-white">{report.summary.total}</p>
                  <p className="mt-1 text-[11px] text-booki-muted">Answered</p>
                </Card>
                <Card className="text-center">
                  <p className="text-xl font-bold text-emerald-400">{report.summary.correct}</p>
                  <p className="mt-1 text-[11px] text-booki-muted">Correct</p>
                </Card>
                <Card className="text-center">
                  <p className="text-xl font-bold text-white">{report.summary.averageScore}%</p>
                  <p className="mt-1 text-[11px] text-booki-muted">Avg. score</p>
                </Card>
              </div>

              <div className="rounded-xl bg-booki-card p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-booki-muted">
                  Email this correction report
                </p>
                <p className="mt-1 text-[11px] text-white/40">
                  Generates a real PDF. Email delivery is simulated in this demo — you can download it instead.
                </p>
                <div className="mt-2 flex gap-2">
                  <Input
                    type="email"
                    value={reportEmail}
                    onChange={(e) => setReportEmail(e.target.value)}
                    placeholder="parent@email.com"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => reportEmail.trim() && send('quiz', reportEmail.trim())}
                    disabled={sending === 'quiz' || !reportEmail.trim()}
                  >
                    {sending === 'quiz' ? 'Sending…' : 'Send'}
                  </Button>
                </div>
                {reportError && <p className="mt-2 text-xs text-rose-400">{reportError}</p>}
                {lastSent && lastSent.type === 'quiz' && (
                  <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-emerald-400">
                    <span>Sent to {lastSent.email} (simulated)</span>
                    <button onClick={() => download(lastSent)} className="font-bold text-white/80 hover:text-white">
                      Download PDF
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
