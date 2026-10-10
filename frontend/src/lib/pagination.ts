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
