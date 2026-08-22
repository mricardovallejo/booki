import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * React Router keeps the browser's scroll position across route changes.
 * Without this, navigating to a shorter page (e.g. Masters) after scrolling
 * down on Home leaves the new page's content hidden behind the fixed header.
 * Skipped when the URL has a hash — useScrollToHash owns that case instead.
 */
export default function ScrollToTop() {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (hash) return;
    window.scrollTo(0, 0);
  }, [pathname, hash]);

  return null;
}
