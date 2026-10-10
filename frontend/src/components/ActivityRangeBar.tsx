import { useEffect, useState } from 'react';
import { useActivityRange } from '../context/ActivityRangeContext';
import { toDisplayPage, toPhysicalPage } from '../lib/pagination';
import type { Document, PageMode } from '../types';

/**
 * Number field you can actually edit on mobile: it holds a free-text draft while
 * focused (so you can clear it and retype) and only clamps + commits on blur or
 * Enter. The +/- buttons commit immediately.
 */
function RangeStepper({
  value,
  min,
  max,
  onChange
}: {
  value: number;
  min: number;
  max: number;
  onChange: (v: number) => void;
}) {
  const [draft, setDraft] = useState(String(value));
  useEffect(() => setDraft(String(value)), [value]);

  const commit = (raw: string) => {
    const n = parseInt(raw, 10);
    const next = Number.isNaN(n) ? value : Math.max(min, Math.min(max, n));
    setDraft(String(next));
    if (next !== value) onChange(next);
  };
  const step = (delta: number) => onChange(Math.max(min, Math.min(max, value + delta)));

  return (
    <span className="inline-flex items-center overflow-hidden rounded-md ring-1 ring-white/15">
      <button
        type="button"
        aria-label="One page fewer"
        onClick={() => step(-1)}
        disabled={value <= min}
        className="px-2.5 py-1 font-bold text-white/70 transition hover:bg-white/10 disabled:opacity-30"
      >
        −
      </button>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        value={draft}
        onChange={(e) => setDraft(e.target.value.replace(/[^0-9]/g, ''))}
        onFocus={(e) => e.target.select()}
        onBlur={(e) => commit(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur();
        }}
        className="w-10 bg-transparent py-1 text-center text-xs font-bold text-white outline-none"
      />
      <button
        type="button"
        aria-label="One page more"
        onClick={() => step(1)}
        disabled={value >= max}
        className="px-2.5 py-1 font-bold text-white/70 transition hover:bg-white/10 disabled:opacity-30"
      >
        +
      </button>
    </span>
  );
}

interface Props {
  /** Null while the document is still loading — the mode toggle and "detect pagination" stay hidden until then. */
  document: Document | null;
  onDetectPagination?: () => Promise<void>;
}

/**
 * The activity page range, above the Chat/Quiz/Progress tabs because it governs
 * all of them (except plain chat). Does not limit reading. `range` from the
 * context is always physical; this is the one place that converts it to/from
 * the reader's chosen display mode (physical or editorial/printed pages).
 */
export default function ActivityRangeBar({ document, onDetectPagination }: Props) {
  const { range, totalPages, pinned, setRange, resetRange, pageMode, setPageMode } = useActivityRange();
  const [detecting, setDetecting] = useState(false);
  if (!range || totalPages <= 0) return null;

  const offset = document?.paginationOffset ?? null;
  const hasOffset = offset != null;
  const mode: PageMode = hasOffset ? pageMode : 'physical';

  const detect = async () => {
    if (!onDetectPagination || detecting) return;
    setDetecting(true);
    try {
      await onDetectPagination();
    } finally {
      setDetecting(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-white/10 bg-booki-bg/40 px-5 py-2 text-xs text-white/60">
      <span title="The pages the quiz, summaries and chat actions use. Does not limit reading.">
        Activities: pages
      </span>
      <span className="inline-flex items-center gap-1.5">
        <RangeStepper
          value={toDisplayPage(range.start, mode, offset)}
          min={toDisplayPage(1, mode, offset)}
          max={toDisplayPage(totalPages, mode, offset)}
          onChange={(v) => setRange({ start: toPhysicalPage(v, mode, offset), end: range.end })}
        />
        <span>–</span>
        <RangeStepper
          value={toDisplayPage(range.end, mode, offset)}
          min={toDisplayPage(1, mode, offset)}
          max={toDisplayPage(totalPages, mode, offset)}
          onChange={(v) => setRange({ start: range.start, end: toPhysicalPage(v, mode, offset) })}
        />
        <span className="text-white/35">/ {toDisplayPage(totalPages, mode, offset)}</span>
      </span>

      {hasOffset ? (
        <span className="inline-flex overflow-hidden rounded-md ring-1 ring-white/15" title="Switch between the PDF's physical pages and this book's own printed page numbers.">
          {(['physical', 'editorial'] as PageMode[]).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setPageMode(m)}
              className={`px-2 py-1 text-[11px] font-bold capitalize transition ${
                mode === m ? 'bg-booki-accent text-white' : 'text-white/60 hover:bg-white/10'
              }`}
            >
              {m === 'physical' ? 'PDF' : 'Printed'}
            </button>
          ))}
        </span>
      ) : (
        onDetectPagination && (
          <button
            type="button"
            onClick={detect}
            disabled={detecting}
            className="text-white/40 underline-offset-2 hover:text-white/70 hover:underline disabled:opacity-40"
            title="Figure out this book's printed page numbers, so you can reference pages the way the book itself does."
          >
            {detecting ? 'Detecting…' : 'Use printed page numbers'}
          </button>
        )
      )}

      {pinned ? (
        <button
          type="button"
          onClick={resetRange}
          className="text-white/40 underline-offset-2 hover:text-white/70 hover:underline"
          title="Go back to following your reading progress"
        >
          reset
        </button>
      ) : (
        <span className="text-white/30" title="Follows how far you have read. Edit to fix it in place.">
          follows your reading
        </span>
      )}
    </div>
  );
}
