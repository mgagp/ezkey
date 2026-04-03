import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/auth-context';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { HelpDrawer } from '@/components/help/help-drawer';
import { isDemoMode } from '@/lib/demo-mode';
import { type HelpPatternId, type HelpTopicId, resolveHelpTopicId } from '@/lib/help-topics';

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.closest('[contenteditable="true"]')) return true;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';
}

export interface OpenHelpOptions {
  topicId?: HelpTopicId;
  patternId?: HelpPatternId;
}

export interface HelpContextValue {
  open: boolean;
  topicId: HelpTopicId;
  patternId: HelpPatternId | null;
  openHelp: (options?: OpenHelpOptions) => void;
  closeHelp: () => void;
  toggleHelp: () => void;
}

const HelpContext = createContext<HelpContextValue | null>(null);

export function HelpProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [overrideTopicId, setOverrideTopicId] = useState<HelpTopicId | null>(null);
  const [patternId, setPatternId] = useState<HelpPatternId | null>(null);
  const routeTopicId = useMemo(() => resolveHelpTopicId(location.pathname), [location.pathname]);
  const topicId = overrideTopicId ?? routeTopicId;

  const openHelp = useCallback((options?: OpenHelpOptions) => {
    setOverrideTopicId(options?.topicId ?? null);
    setPatternId(options?.patternId ?? null);
    setOpen(true);
  }, []);
  const closeHelp = useCallback(() => {
    setOpen(false);
    setOverrideTopicId(null);
    setPatternId(null);
  }, []);
  const toggleHelp = useCallback(() => setOpen((o) => !o), []);

  useEffect(() => {
    setOverrideTopicId(null);
    setPatternId(null);
  }, [location.pathname]);

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

export function useHelp(): HelpContextValue {
  const ctx = useContext(HelpContext);
  if (!ctx) {
    throw new Error('useHelp must be used within HelpProvider');
  }
  return ctx;
}
