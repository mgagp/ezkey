import { useEffect, useRef } from 'react';

export interface DetailNavigationOptions {
  /** When false, arrow keys do nothing. Defaults to true when omitted. */
  enabled?: boolean;
  hasPrev?: boolean;
  hasNext?: boolean;
  onPrev?: () => void;
  onNext?: () => void;
}

function isEditableTarget(target: EventTarget | null): boolean {
  if (target == null || !(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (target.isContentEditable) return true;
  return false;
}

/**
 * Registers Left/Right arrow keys for prev/next detail navigation while a detail view is open.
 * Skips handling when focus is in an input, textarea, select, or contenteditable (so typing is unaffected).
 */
export function useDetailNavigation(isActive: boolean, options: DetailNavigationOptions): void {
  const optsRef = useRef(options);
  optsRef.current = options;

  useEffect(() => {
    if (!isActive) return;

    const handler = (e: KeyboardEvent) => {
      const o = optsRef.current;
      if (o.enabled === false) return;
      if (isEditableTarget(e.target)) return;
      if (e.key === 'ArrowLeft' && o.hasPrev) {
        e.preventDefault();
        o.onPrev?.();
      } else if (e.key === 'ArrowRight' && o.hasNext) {
        e.preventDefault();
        o.onNext?.();
      }
    };

    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isActive]);
}
