import { HelpCircle } from 'lucide-react';
import { type ReactNode, useRef, useState, useEffect } from 'react';
import { cn } from '@/lib/utils';

export interface ContextHelpProps {
  /** Short title shown in the popover header. */
  title: string;
  /** Body content: string or ReactNode (e.g. paragraph + link). */
  content: ReactNode;
  /** Optional "Learn more" URL (opens in new tab). */
  learnMoreUrl?: string;
  /** Optional class for the trigger button. */
  className?: string;
  /** Accessible label for the icon button. */
  ariaLabel?: string;
}

/**
 * In-context help: click-to-open icon that shows a small popover with title and content.
 * Use next to section titles or actions to explain technical concepts without clutter.
 * Accessible and works on touch (click, not hover-only).
 */
export function ContextHelp({
  title,
  content,
  learnMoreUrl,
  className,
  ariaLabel = 'Help',
}: ContextHelpProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  return (
    <div ref={containerRef} className="relative inline-flex">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'p-0.5 rounded-sm text-fg-muted hover:text-fg hover:bg-fg/10 transition-colors',
          open && 'text-fg bg-fg/10',
          className,
        )}
        aria-label={ariaLabel}
        aria-expanded={open}
      >
        <HelpCircle className="size-3.5" />
      </button>
      {open && (
        <div
          role="dialog"
          aria-label={title}
          className="absolute left-full top-0 ml-1.5 z-50 w-72 bg-surface border-2 border-fg shadow-brutal p-3 text-left"
        >
          <p className="text-xs font-bold uppercase tracking-wider text-fg mb-1.5">{title}</p>
          <div className="text-sm text-fg-muted space-y-1.5">{content}</div>
          {learnMoreUrl && (
            <a
              href={learnMoreUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-2 inline-block text-xs font-bold text-accent hover:underline"
            >
              Learn more →
            </a>
          )}
        </div>
      )}
    </div>
  );
}
