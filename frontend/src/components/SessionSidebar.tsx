import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '../config/routes';
import ChatPanel from './ChatPanel';
import QuizPanel from './QuizPanel';
import ProgressPanel from './ProgressPanel';
import NotificationsBell from './NotificationsBell';
import ContextInfoButton from './ContextInfoButton';
import { useSessionContext } from '../hooks/useSessionContext';

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
  const context = useSessionContext(sessionId);
  const [tab, setTab] = useState<Tab>('chat');
  const [refreshKey, setRefreshKey] = useState(0);
  // On mobile the panel is a drawer that floats OVER the PDF instead of
  // sharing the layout with it. It used to always reserve space in the flex
  // column (a header + tab bar at minimum), which in landscape — where the
  // viewport is short — left barely any height for the PDF. As an overlay,
  // the PDF gets the full screen whenever the drawer is closed.
  const [open, setOpen] = useState(false);

  const bumpActivity = () => setRefreshKey((k) => k + 1);

  const onSelectTab = (t: Tab) => {
    setTab(t);
    setOpen(true);
  };

  const panelBody = (
    <>
      <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
        <div className="min-w-0">
          <h3 className="font-logo text-lg leading-tight text-white">BooKI</h3>
          {context?.aiProfileName && context.aiProfileId ? (
            <Link
              to={ROUTES.aiProfile(context.aiProfileId)}
              title="This session's AI Profile — click to edit"
              className="mt-1 inline-flex max-w-full items-center gap-1.5 rounded-full bg-booki-accent/10 py-0.5 pl-1.5 pr-2 text-[11px] font-semibold text-booki-accent ring-1 ring-inset ring-booki-accent/25 transition hover:bg-booki-accent/20 hover:ring-booki-accent/40"
            >
              <svg className="h-3 w-3 shrink-0" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M12 2.5l2.35 5.68 6.15.53-4.68 4.03 1.42 6.01L12 15.9 6.76 18.76l1.42-6.01L3.5 8.71l6.15-.53L12 2.5z" />
              </svg>
              <span className="truncate">{context.aiProfileName}</span>
            </Link>
          ) : (
            <p className="mt-0.5 truncate text-xs text-booki-muted">Reading assistant</p>
          )}
        </div>
        <div className="flex items-center gap-2">
          <ContextInfoButton sessionId={sessionId} />
          <NotificationsBell sessionId={sessionId} refreshKey={refreshKey} />
          <div className="flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.6)]" />
          <button
            onClick={() => setOpen(false)}
            className="rounded-full bg-white/5 p-2 text-white/80 transition hover:bg-white/10 hover:text-white md:hidden"
            title="Hide panel (see the PDF)"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
        </div>
      </div>

      <div className="flex border-b border-white/10 px-5">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => onSelectTab(t.id)}
            className={`font-menu relative px-3 py-3 text-[11px] tracking-wide transition ${
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
        {tab === 'progress' && <ProgressPanel sessionId={sessionId} refreshKey={refreshKey} />}
      </div>
    </>
  );

  return (
    <>
      {/* Desktop / tablet: static side panel, always visible, unchanged. */}
      <aside className="hidden md:flex md:h-auto md:w-[420px] md:flex-col md:border-l md:border-white/10 md:bg-booki-surface/95 md:shadow-2xl md:backdrop-blur">
        {panelBody}
      </aside>

      {/* Mobile: closed state is just a small floating button — costs no layout space. */}
      {!open && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-5 right-5 z-40 flex items-center gap-2 rounded-full bg-booki-accent px-4 py-3 text-sm font-bold text-white shadow-2xl md:hidden"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M21 12c0 4.418-4.03 8-9 8a9.86 9.86 0 01-4-.8L3 20l1.3-3.9A7.9 7.9 0 013 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
            />
          </svg>
          <span className="font-logo text-base">BooKI</span>
        </button>
      )}

      {/* Mobile: open state is a bottom-sheet drawer over the PDF. */}
      {open && (
        <div className="fixed inset-0 z-40 flex flex-col justify-end md:hidden">
          <div className="absolute inset-0 bg-black/60" onClick={() => setOpen(false)} />
          <div className="relative flex h-[85vh] max-h-[85dvh] flex-col rounded-t-2xl border-t border-white/10 bg-booki-surface shadow-2xl">
            {panelBody}
          </div>
        </div>
      )}
    </>
  );
}
