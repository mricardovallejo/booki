import { useQuizReport } from '../hooks/useQuizReport';
import Card from './ui/Card';
import SendReportForm from './SendReportForm';

interface Props {
  sessionId: number;
  refreshKey: number;
}

export default function QuizReportPanel({ sessionId, refreshKey }: Props) {
  const { report, error } = useQuizReport(sessionId, refreshKey);

  if (error) {
    return (
      <div className="flex h-full items-center justify-center px-5 text-center text-sm text-rose-400">
        {error}
      </div>
    );
  }

  if (!report) {
    return (
      <div className="flex h-full items-center justify-center text-booki-muted">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-white/20 border-t-booki-accent" />
      </div>
    );
  }

  if (report.attempts.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-5 text-center text-booki-muted">
        <p className="max-w-[220px] text-sm">
          No quiz answers yet. Take a quiz to see your correction report here.
        </p>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto px-5 py-4">
      <div className="mb-4 grid grid-cols-3 gap-3">
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

      <div className="space-y-3">
        {report.attempts.map((a) => (
          <Card key={a.id}>
            <div className="flex items-center justify-between">
              <p className="text-xs font-semibold uppercase tracking-wide text-booki-muted">
                Page {a.pageNumber} · {a.difficulty}
                {a.masterName ? ` · ${a.masterName}` : ''}
              </p>
              <span
                className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                  a.correct ? 'bg-emerald-500/20 text-emerald-300' : 'bg-amber-500/20 text-amber-300'
                }`}
              >
                {a.correct ? 'Correct' : 'Needs work'}
              </span>
            </div>
            <p className="mt-2 text-sm font-medium text-white">{a.question}</p>
            <p className="mt-1 text-sm text-white/70">"{a.answer}"</p>
            <p className="mt-2 text-xs text-booki-muted">{a.feedback}</p>
          </Card>
        ))}
      </div>

      <div className="mt-6">
        <SendReportForm sessionId={sessionId} type="quiz" label="Email this correction report" />
      </div>
    </div>
  );
}
