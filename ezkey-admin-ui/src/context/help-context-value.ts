import { createContext } from 'react';
import type { HelpPatternId, HelpTopicId } from '@/lib/help-topics';

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

export const HelpContext = createContext<HelpContextValue | null>(null);
