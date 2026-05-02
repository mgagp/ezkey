import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/use-auth';
import { useDemoModeSession } from '@/context/use-demo-mode-session';
import { HelpDrawer } from '@/components/help/help-drawer';
import { isDemoMode } from '@/lib/demo-mode';
import { type HelpPatternId, type HelpTopicId, resolveHelpTopicId } from '@/lib/help-topics';
import { HelpContext } from '@/context/help-context-value';
import type { OpenHelpOptions } from '@/context/help-context-value';

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.closest('[contenteditable="true"]')) return true;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';
}

export function HelpProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [override, setOverride] = useState<{
    pathname: string;
    topicId: HelpTopicId | null;
    patternId: HelpPatternId | null;
  } | null>(null);
  const routeTopicId = useMemo(() => resolveHelpTopicId(location.pathname), [location.pathname]);
  const activeOverride = override?.pathname === location.pathname ? override : null;
  const topicId = activeOverride?.topicId ?? routeTopicId;
  const patternId = activeOverride?.patternId ?? null;

  const openHelp = useCallback((options?: OpenHelpOptions) => {
    setOverride({
      pathname: location.pathname,
      topicId: options?.topicId ?? null,
      patternId: options?.patternId ?? null,
    });
    setOpen(true);
  }, [location.pathname]);
  const closeHelp = useCallback(() => {
    setOpen(false);
    setOverride(null);
  }, []);
  const toggleHelp = useCallback(() => setOpen((o) => !o), []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== '?') return;
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (isEditableTarget(e.target)) return;
      e.preventDefault();
      setOpen(true);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const value = useMemo(
    () => ({
      open,
      topicId,
      patternId,
      openHelp,
      closeHelp,
      toggleHelp,
    }),
    [open, topicId, patternId, openHelp, closeHelp, toggleHelp],
  );

  return (
    <HelpContext.Provider value={value}>
      {children}
      <HelpDrawerShell open={open} onClose={closeHelp} topicId={topicId} patternId={patternId} />
    </HelpContext.Provider>
  );
}

function HelpDrawerShell({
  open,
  onClose,
  topicId,
  patternId,
}: {
  open: boolean;
  onClose: () => void;
  topicId: HelpTopicId;
  patternId: HelpPatternId | null;
}) {
  const { session } = useAuth();
  const { sessionDemoOn } = useDemoModeSession();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const showDemoExtra = isDemoMode && sessionDemoOn;

  return (
    <HelpDrawer
      open={open}
      onClose={onClose}
      topicId={topicId}
      patternId={patternId}
      isGlobalAdmin={isGlobalAdmin}
      showDemoExtra={showDemoExtra}
    />
  );
}

