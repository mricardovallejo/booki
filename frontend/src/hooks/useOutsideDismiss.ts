import { useEffect, useRef } from 'react';

/**
 * Dismiss a popover / dropdown menu when the user clicks outside it or presses
 * Escape. Attach the returned ref to the popover's outermost element (the one
 * that also contains its trigger button, so clicking the trigger doesn't count
 * as "outside").
 *
 * `onClose` is read through a ref, so callers can pass an inline arrow function
 * without re-subscribing the listeners on every render.
 */
export function useOutsideDismiss<T extends HTMLElement = HTMLDivElement>(
  open: boolean,
  onClose: () => void
) {
  const ref = useRef<T>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;

    const onPointerDown = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onCloseRef.current();
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCloseRef.current();
    };

    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return ref;
}
