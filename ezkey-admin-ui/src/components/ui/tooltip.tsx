import { cn } from '@/lib/utils';
import {
  type ReactNode,
  useState,
  useRef,
  useEffect,
  useCallback,
  useLayoutEffect,
} from 'react';
import { createPortal } from 'react-dom';

const HOVER_DELAY_MS = 300;
const GAP_PX = 6;

export interface TooltipProps {
  /** Short text shown on hover (one sentence, max ~15 words). */
  content: string;
  /** Placement relative to the trigger element. */
  position?: 'top' | 'bottom';
  /** Trigger element(s). */
  children: ReactNode;
  /** Optional class for the wrapper. */
  className?: string;
}

/**
 * Hover tooltip for quick "what does this mean?" context.
 * Renders via portal so it is not clipped by parent overflow (e.g. table).
 * Shows after a short delay to avoid flicker on mouse pass-through.
 * Keyboard accessible: shows on focus, hides on blur.
 * Neo-brutalist styling to match the Admin UI.
 */
export function Tooltip({
  content,
  position = 'top',
  children,
  className,
}: TooltipProps) {
  const [visible, setVisible] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);

  const clearDelay = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  const updatePosition = useCallback(() => {
    const wrapper = wrapperRef.current;
    const tooltipEl = tooltipRef.current;
    if (!wrapper || !tooltipEl) return;
    const rect = wrapper.getBoundingClientRect();
    const tooltipRect = tooltipEl.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    let top: number;
    if (position === 'top') {
      top = rect.top - tooltipRect.height - GAP_PX;
    } else {
      top = rect.bottom + GAP_PX;
    }
    let left = centerX - tooltipRect.width / 2;
    const maxLeft = document.documentElement.clientWidth - tooltipRect.width - 8;
    left = Math.max(8, Math.min(left, maxLeft));
    setCoords({ top, left });
  }, [position]);

  useLayoutEffect(() => {
    if (!visible) return;
    updatePosition();
  }, [visible, content, updatePosition]);

  useEffect(() => {
    if (!visible) return;
    const handleScrollOrResize = () => updatePosition();
    window.addEventListener('scroll', handleScrollOrResize, true);
    window.addEventListener('resize', handleScrollOrResize);
    return () => {
      window.removeEventListener('scroll', handleScrollOrResize, true);
      window.removeEventListener('resize', handleScrollOrResize);
    };
  }, [visible, updatePosition]);

  const show = useCallback(() => {
    clearDelay();
    timeoutRef.current = setTimeout(() => setVisible(true), HOVER_DELAY_MS);
  }, [clearDelay]);

  const showImmediate = useCallback(() => {
    clearDelay();
    setVisible(true);
  }, [clearDelay]);

  const hide = useCallback(() => {
    clearDelay();
    setVisible(false);
    setCoords(null);
  }, [clearDelay]);

  useEffect(() => {
    return () => clearDelay();
  }, [clearDelay]);

  const tooltipContent = visible ? (
    <div
      ref={tooltipRef}
      role="tooltip"
      className={cn(
        'fixed z-[9999] max-w-[220px] px-2.5 py-1.5 text-sm text-fg bg-surface border-2 border-fg shadow-brutal whitespace-normal',
      )}
      style={
        coords
          ? { top: coords.top, left: coords.left }
          : { top: -9999, left: -9999, visibility: 'hidden' as const }
      }
    >
      {content}
      {/* Arrow pointing to trigger */}
      <span
        className={cn(
          'absolute left-1/2 -translate-x-1/2 w-0 h-0 border-[6px] border-transparent',
          position === 'top' &&
            'top-full mt-[-2px] border-t-fg border-x-transparent border-b-transparent',
          position === 'bottom' &&
            'bottom-full mb-[-2px] border-b-fg border-x-transparent border-t-transparent',
        )}
        aria-hidden
      />
    </div>
  ) : null;

  return (
    <div
      ref={wrapperRef}
      className={cn('relative inline-flex', className)}
      onMouseEnter={show}
      onMouseLeave={hide}
      onFocusCapture={showImmediate}
      onBlurCapture={hide}
    >
      {children}
      {tooltipContent && createPortal(tooltipContent, document.body)}
    </div>
  );
}
