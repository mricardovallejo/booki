import { useState } from 'react';
import { useSessionContext } from '../hooks/useSessionContext';
import { useSession } from '../hooks/useSession';
import { AI_PROVIDER_LABELS } from '../lib/aiProviders';
import type { SessionContextGroup, SessionContextLayer } from '../types';

interface Props {
  sessionId: number;
}

// Order the layers appear in, and which groups are heavy enough to fold away.
const GROUP_ORDER: SessionContextGroup[] = [
  'core',
  'difficulty',
  'persona',
  'reader',
  'session',
  'functions',
  'routing'
];
const FOLDED_BY_DEFAULT: SessionContextGroup[] = ['functions', 'routing'];
const GROUP_HEADING: Record<SessionContextGroup, string> = {
  core: 'Core',
  difficulty: 'Difficulty',
  persona: 'Persona',
  reader: 'Reader',
  session: 'This session',
  functions: 'Per-action instructions',
  routing: 'Auto-actions'
};

function LayerBlock({ layer }: { layer: SessionContextLayer }) {
  return (
    <div>
      <p className="flex items-center gap-1.5 font-bold text-white/70">
        {layer.label}
        {!layer.editable && (
          <span className="rounded bg-white/10 px-1 py-px text-[9px] font-bold uppercase text-white/40">
            fixed
          </span>
        )}
      </p>
      <p className="mt-0.5 whitespace-pre-wrap text-white/60">{layer.content || '— not set —'}</p>
      <p className="mt-0.5 text-[10px] text-white/30">{layer.source}</p>
    </div>
  );
}

export default function ContextInfoButton({ sessionId }: Props) {
  const context = useSessionContext(sessionId);
  const { session } = useSession(sessionId);
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<SessionContextGroup>>(new Set());

  const toggle = (g: SessionContextGroup) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(g) ? next.delete(g) : next.add(g);
      return next;
    });

  const grouped = (context?.layers ?? []).reduce<Record<string, SessionContextLayer[]>>((acc, l) => {
    (acc[l.group] ??= []).push(l);
    return acc;
  }, {});

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="rounded-full bg-white/5 p-2 text-white/80 transition hover:bg-white/10 hover:text-white"
        title="Everything BooKI reads before answering"
      >
        <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </button>
      {open && context && (
        <div className="absolute right-0 top-full z-20 mt-2 max-h-[75vh] w-96 overflow-y-auto rounded-lg bg-booki-surface p-4 shadow-2xl ring-1 ring-white/10">
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-booki-muted">
            Everything BooKI reads before answering
          </p>
          <p className="mb-3 text-[11px] text-white/40">
            AI model: {session ? AI_PROVIDER_LABELS[session.aiProvider] : '…'} · Profile:{' '}
            {context.aiProfileName || '—'}
          </p>

          <div className="space-y-4 text-xs">
            {GROUP_ORDER.filter((g) => grouped[g]?.length).map((g) => {
              const layers = grouped[g];
              const foldable = FOLDED_BY_DEFAULT.includes(g);
              const isOpen = !foldable || expanded.has(g);
              return (
                <div key={g}>
                  {foldable ? (
                    <button
                      onClick={() => toggle(g)}
                      className="mb-1.5 flex w-full items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-booki-muted hover:text-white"
                    >
                      <span>{isOpen ? '▾' : '▸'}</span>
                      {GROUP_HEADING[g]} ({layers.length})
                    </button>
                  ) : (
                    <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wide text-booki-muted">
                      {GROUP_HEADING[g]}
                    </p>
                  )}
                  {isOpen && (
                    <div className="space-y-3">
                      {layers.map((layer) => (
                        <LayerBlock key={layer.key} layer={layer} />
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
