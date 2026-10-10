import type { PageMode } from '../types';

/**
 * Physical↔editorial page conversion, shared by every component that shows a
 * page number — the PDF viewer and the activity range bar must agree on the
 * same math, or the same number shown in both places can silently mean two
 * different physical pages (confusing a reader who assumes they match).
 * Mirrors the backend's `PageReference`. See ADR-030.
 */
export function toDisplayPage(physicalPage: number, mode: PageMode, offset: number | null): number {
  return mode === 'editorial' && offset != null ? physicalPage - offset : physicalPage;
}

export function toPhysicalPage(displayedPage: number, mode: PageMode, offset: number | null): number {
  return mode === 'editorial' && offset != null ? displayedPage + offset : displayedPage;
}

/**
 * Physical pages before printed page "1" (cover, dedication, table of
 * contents…) have no printed number — showing them as 0, -1, -2 reads as a
 * bug. In editorial mode such pages are labelled by their PDF index instead.
 */
export function isFrontMatter(physicalPage: number, mode: PageMode, offset: number | null): boolean {
  return mode === 'editorial' && offset != null && physicalPage <= offset;
}

export function formatDisplayPage(physicalPage: number, mode: PageMode, offset: number | null): string {
  return isFrontMatter(physicalPage, mode, offset)
    ? `PDF ${physicalPage}`
    : String(toDisplayPage(physicalPage, mode, offset));
}
