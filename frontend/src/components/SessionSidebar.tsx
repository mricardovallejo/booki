import { useState } from 'react';
import ChatPanel from './ChatPanel';
import QuizPanel from './QuizPanel';
import QuizReportPanel from './QuizReportPanel';
import ProgressPanel from './ProgressPanel';
import NotificationsBell from './NotificationsBell';
import ContextInfoButton from './ContextInfoButton';

interface Props {
  sessionId: number;
}

type Tab = 'chat' | 'quiz' | 'report' | 'progress';

const TABS: { id: Tab; label: string }[] = [
  { id: 'chat', label: 'Chat' },
  { id: 'quiz', label: 'Quiz' },
  { id: 'report', label: 'Report' },
  { id: 'progress', label: 'Progress' }
];

export default function SessionSidebar({ sessionId }: Props) {
  const [tab, setTab] = useState<Tab>('chat');
  const [refreshKey, setRefreshKey] = useState(0);

  const bumpActivity = () => setRefreshKey((k) => k + 1);

  return (
    <aside className="flex h-[60vh] flex-col border-t border-white/10 bg-booki-surface/95 shadow-2xl backdrop-blur md:h-auto md:w-[420px] md:border-l md:border-t-0">
      <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
        <div>
          <h3 className="font-bold text-white">BooKI</h3>
          <p className="text-xs text-booki-muted">Reading assistant</p>
        </div>
        <div className="flex items-center gap-2">
          <ContextInfoButton sessionId={sessionId} />
          <NotificationsBell sessionId={sessionId} refreshKey={refreshKey} />
          <div className="flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.6)]" />
        </div>
      </div>

      <div className="flex border-b border-white/10 px-5">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`relative px-3 py-3 text-sm font-medium transition ${
              tab === t.id ? 'text-white' : 'text-white/50 hover:text-white/80'
            }`}
          >
            {t.label}
            {tab === t.id && (
              <span className="absolute bottom-0 left-0 h-0.5 w-full bg-booki-accent" />
            )}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-hidden">
        {tab === 'chat' && <ChatPanel sessionId={sessionId} onActivity={bumpActivity} />}
        {tab === 'quiz' && <QuizPanel sessionId={sessionId} onActivity={bumpActivity} />}
        {tab === 'report' && <QuizReportPanel sessionId={sessionId} refreshKey={refreshKey} />}
        {tab === 'progress' && <ProgressPanel sessionId={sessionId} refreshKey={refreshKey} />}
      </div>
    </aside>
  );
}
