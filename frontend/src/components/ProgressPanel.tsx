import { useProgress } from '../hooks/useProgress';
import SendReportForm from './SendReportForm';

interface Props {
  sessionId: number;
  refreshKey: number;
}

export default function ProgressPanel({ sessionId, refreshKey }: Props) {
  const progress = useProgress(sessionId, refreshKey);

  if (!progress) {
    return (
      <div className="flex h-full items-center justify-center text-booki-muted">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-white/20 border-t-booki-accent" />
      </div>
    );
  }

  const stats = [
    { label: 'Pages read', value: `${progress.pagesRead}/${progress.totalPages}` },
    { label: 'Messages exchanged', value: progress.messageCount },
    { label: 'Quizzes taken', value: progress.quizzesTaken },
    { label: 'Quiz average score', value: `${progress.quizAverageScore}%` }
  ];

  return (
    <div className="h-full overflow-y-auto px-5 py-5">
      <div>
        <div className="flex items-center justify-between text-xs text-booki-muted">
          <span>Reading progress</span>
          <span>{progress.pctRead}%</span>
        </div>
        <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-booki-card">
          <div
            className="h-full rounded-full bg-booki-accent transition-all"
            style={{ width: `${progress.pctRead}%` }}
          />
        </div>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-3">
        {stats.map((s) => (
          <div key={s.label} className="rounded-xl bg-booki-card p-4">
            <p className="text-xl font-bold text-white">{s.value}</p>
            <p className="mt-1 text-xs text-booki-muted">{s.label}</p>
          </div>
        ))}
      </div>

      {progress.pctRead >= 100 && (
        <div className="mt-6 rounded-xl bg-emerald-500/10 p-4 text-sm text-emerald-300">
          🎉 You've read every page in this session. Great job!
        </div>
      )}

      <div className="mt-6">
        <SendReportForm sessionId={sessionId} type="progress" label="Email this progress report" />
      </div>
    </div>
  );
}
