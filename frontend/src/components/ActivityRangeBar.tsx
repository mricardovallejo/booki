import { useEffect, useState } from 'react';
import { useActivityRange } from '../context/ActivityRangeContext';

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

/**
 * The activity page range, above the Chat/Quiz/Progress tabs because it governs
 * all of them (except plain chat). Does not limit reading.
 */
export default function ActivityRangeBar() {
  const { range, totalPages, pinned, setRange, resetRange } = useActivityRange();
  if (!range || totalPages <= 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-white/10 bg-booki-bg/40 px-5 py-2 text-xs text-white/60">
      <span title="The pages the quiz, summaries and chat actions use. Does not limit reading.">
        Activities: pages
      </span>
      <span className="inline-flex items-center gap-1.5">
        <RangeStepper
          value={range.start}
          min={1}
          max={totalPages}
          onChange={(v) => setRange({ start: v, end: range.end })}
        />
        <span>–</span>
        <RangeStepper
          value={range.end}
          min={1}
          max={totalPages}
          onChange={(v) => setRange({ start: range.start, end: v })}
        />
        <span className="text-white/35">/ {totalPages}</span>
      </span>
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
