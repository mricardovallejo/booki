import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode
} from 'react';

/**
 * The page range the AI "functions" run on — the panel quiz, the summary modal,
 * and the chat quick-actions (Ask me / Explain / Summarize / Mnemonic). Plain
 * chat text is NOT bound to it; it stays on the reading position.
 *
 * It is not a property of the session. By default it runs from page 1 to the
 * furthest page the reader has reached, and keeps following as they read on — so
 * a quiz never asks about a chapter they haven't opened yet. Once the reader
 * edits it, their choice sticks (but the end still grows if they read past it).
 * Scoped per session id (SessionPage remounts the provider).
 */
export interface ActivityRange {
  start: number;
  end: number;
}

interface ActivityRangeValue {
  /** Effective range. Null only until the document length is known. */
  range: ActivityRange | null;
  totalPages: number;
  /** True once the reader has overridden the default (pages read so far). */
  pinned: boolean;
  setRange: (range: ActivityRange) => void;
  /** Drop the override — back to "pages read so far". */
  resetRange: () => void;
  /** The PDF viewer reports the document length. */
  initTotalPages: (total: number) => void;
  /** The PDF viewer reports how far the reader has got (session.endPage). */
  setReadEnd: (page: number) => void;
}

const ActivityRangeContext = createContext<ActivityRangeValue | null>(null);

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

export function ActivityRangeProvider({ children }: { children: ReactNode }) {
  const [pinned, setPinned] = useState<ActivityRange | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [readEnd, setReadEndState] = useState(1);

  const setRange = useCallback((next: ActivityRange) => {
    const a = Math.max(1, Math.round(next.start) || 1);
    const b = Math.max(1, Math.round(next.end) || 1);
    setPinned({ start: Math.min(a, b), end: Math.max(a, b) });
  }, []);

  const resetRange = useCallback(() => setPinned(null), []);

  const initTotalPages = useCallback((total: number) => {
    if (total > 0) setTotalPages(total);
  }, []);

  const setReadEnd = useCallback((page: number) => {
    if (page > 0) setReadEndState(page);
  }, []);

  const range = useMemo<ActivityRange | null>(() => {
    if (totalPages <= 0) return null;
    const reached = clamp(readEnd, 1, totalPages);
    // Default: everything read so far, following reading progress.
    if (!pinned) return { start: 1, end: reached };
    const start = clamp(pinned.start, 1, totalPages);
    // The reader's end stays put, but never below where they have actually read.
    const end = clamp(Math.max(pinned.end, reached), start, totalPages);
    return { start, end };
  }, [pinned, totalPages, readEnd]);

  const value = useMemo(
    () => ({
      range,
      totalPages,
      pinned: pinned != null,
      setRange,
      resetRange,
      initTotalPages,
      setReadEnd
    }),
    [range, totalPages, pinned, setRange, resetRange, initTotalPages, setReadEnd]
  );

  return <ActivityRangeContext.Provider value={value}>{children}</ActivityRangeContext.Provider>;
}

// react-refresh rule only cares about HMR granularity, not correctness.
// eslint-disable-next-line react-refresh/only-export-components
export function useActivityRange() {
  const ctx = useContext(ActivityRangeContext);
  if (!ctx) throw new Error('useActivityRange must be used within an ActivityRangeProvider');
  return ctx;
}
