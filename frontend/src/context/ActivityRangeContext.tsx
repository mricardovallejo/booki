import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode
} from 'react';
import type { PageMode } from '../types';

/**
 * The page range the AI "functions" run on — the panel quiz, the summary modal,
 * and the chat quick-actions (Ask me / Explain / Summarize / Mnemonic). Plain
 * chat text is NOT bound to it; it stays on the reading position.
 *
 * It is not a property of the session. By default it runs from page 1 to the
 * furthest page the reader has reached, and keeps following as they read on — so
 * a quiz never asks about a chapter they haven't opened yet. Once the reader
 * edits it, their choice is exact and always editable again — reading further
 * does NOT silently grow it back (that surprised readers trying to narrow the
 * range below a page they'd already reached: the edit looked ignored). Press
 * "reset" to drop back to following the reading position.
 * Scoped per session id (SessionPage remounts the provider).
 */
export interface ActivityRange {
  start: number;
  end: number;
}

interface ActivityRangeValue {
  /** Effective range, ALWAYS in physical pages regardless of pageMode. Null only until the document length is known. */
  range: ActivityRange | null;
  totalPages: number;
  /**
   * Lowest physical page the range can start on: 1, or — in editorial mode —
   * the physical page of printed page "1", so front matter (no printed
   * number) never shows up as printed page 0 or -3.
   */
  firstPage: number;
  /** True once the reader has overridden the default (pages read so far). */
  pinned: boolean;
  setRange: (range: ActivityRange) => void;
  /** Drop the override — back to "pages read so far". */
  resetRange: () => void;
  /** The PDF viewer reports the document length. */
  initTotalPages: (total: number) => void;
  /** The PDF viewer reports how far the reader has got (session.endPage). */
  setReadEnd: (page: number) => void;
  /**
   * How `range` is DISPLAYED and EDITED (ActivityRangeBar) and sent to the
   * backend with (chat/quiz/summary) — purely a UI/wire concern, `range`
   * itself always stays physical. Defaults to physical; a page only offers
   * "editorial" once its Document has a known pagination offset — this is
   * the EFFECTIVE mode, forced to physical while the offset is unknown.
   */
  pageMode: PageMode;
  setPageMode: (mode: PageMode) => void;
  /**
   * The session document's paginationOffset, held here once so the PDF viewer
   * and the activity range bar can never disagree on it (each used to fetch
   * the document on its own, and only one refreshed after a detection).
   * Published by SessionSidebar, which owns the document fetch.
   */
  paginationOffset: number | null;
  setPaginationOffset: (offset: number | null) => void;
}

const ActivityRangeContext = createContext<ActivityRangeValue | null>(null);

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

export function ActivityRangeProvider({ children }: { children: ReactNode }) {
  const [pinned, setPinned] = useState<ActivityRange | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [readEnd, setReadEndState] = useState(1);
  const [chosenMode, setPageMode] = useState<PageMode>('physical');
  const [paginationOffset, setPaginationOffset] = useState<number | null>(null);
  const pageMode: PageMode = paginationOffset != null ? chosenMode : 'physical';

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

  const firstPage =
    pageMode === 'editorial' && paginationOffset != null && totalPages > 0
      ? clamp(paginationOffset + 1, 1, totalPages)
      : 1;

  const range = useMemo<ActivityRange | null>(() => {
    if (totalPages <= 0) return null;
    const reached = clamp(readEnd, firstPage, totalPages);
    // Default: everything read so far, following reading progress.
    if (!pinned) return { start: firstPage, end: reached };
    // Pinned: exactly what the reader set, full stop — no silent floor at
    // `reached`. "reset" (below) is the way back to auto-following.
    const start = clamp(pinned.start, firstPage, totalPages);
    const end = clamp(pinned.end, start, totalPages);
    return { start, end };
  }, [pinned, totalPages, readEnd, firstPage]);

  const value = useMemo(
    () => ({
      range,
      totalPages,
      firstPage,
      pinned: pinned != null,
      setRange,
      resetRange,
      initTotalPages,
      setReadEnd,
      pageMode,
      setPageMode,
      paginationOffset,
      setPaginationOffset
    }),
    [range, totalPages, firstPage, pinned, setRange, resetRange, initTotalPages, setReadEnd, pageMode, paginationOffset]
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
