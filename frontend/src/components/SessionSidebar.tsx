import { useState } from 'react';
import ChatPanel from './ChatPanel';
import QuizPanel from './QuizPanel';
import ProgressPanel from './ProgressPanel';
import NotificationsBell from './NotificationsBell';
import ContextInfoButton from './ContextInfoButton';

interface Props {
  sessionId: number;
}

type Tab = 'chat' | 'quiz' | 'progress';

const TABS: { id: Tab; label: string }[] = [
  { id: 'chat', label: 'Chat' },
  { id: 'quiz', label: 'Quiz' },
  { id: 'progress', label: 'Progress' }
];

export default function SessionSidebar({ sessionId }: Props) {
  const [tab, setTab] = useState<Tab>('chat');
  const [refreshKey, setRefreshKey] = useState(0);
  // Collapsed by default on mobile — the panel used to permanently reserve 60vh
  // (h-[60vh]) there, squeezing the PDF into a sliver in both orientations.
  // On desktop (md:) this is ignored entirely; the panel is always fully shown.
  const [collapsed, setCollapsed] = useState(true);

  const bumpActivity = () => setRefreshKey((k) => k + 1);

  const onSelectTab = (t: Tab) => {
    setTab(t);
    setCollapsed(false);
  };

  return (
    <aside
      className={`flex flex-col border-t border-white/10 bg-booki-surface/95 shadow-2xl backdrop-blur md:h-auto md:w-[420px] md:border-l md:border-t-0 ${
        collapsed ? 'h-auto' : 'h-[60vh]'
      }`}
    >
      <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
        <div>
          <h3 className="font-bold text-white">BooKI</h3>
          <p className="text-xs text-booki-muted">Reading assistant</p>
        </div>
        <div className="flex items-center gap-2">
          <ContextInfoButton sessionId={sessionId} />
          <NotificationsBell sessionId={sessionId} refreshKey={refreshKey} />
          <div className="flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.6)]" />
          <button
            onClick={() => setCollapsed((c) => !c)}
            className="rounded-full bg-white/5 p-2 text-white/80 transition hover:bg-white/10 hover:text-white md:hidden"
            title={collapsed ? 'Show panel' : 'Hide panel (see more of the PDF)'}
          >
            <svg
              className={`h-4 w-4 transition-transform ${collapsed ? '' : 'rotate-180'}`}
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 15l7-7 7 7" />
            </svg>
          </button>
        </div>
      </div>

      <div className="flex border-b border-white/10 px-5">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => onSelectTab(t.id)}
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

      <div className={`flex-1 overflow-hidden ${collapsed ? 'hidden md:block' : 'block'}`}>
        {tab === 'chat' && <ChatPanel sessionId={sessionId} onActivity={bumpActivity} />}
        {tab === 'quiz' && <QuizPanel sessionId={sessionId} onActivity={bumpActivity} />}
        {tab === 'progress' && <ProgressPanel sessionId={sessionId} refreshKey={refreshKey} />}
      </div>
    </aside>
  );
}
