import { createContext } from 'react';
import type { HelpTopicId } from '@/lib/help-topics';

export interface OpenHelpOptions {
  topicId?: HelpTopicId;
}

export interface HelpContextValue {
  open: boolean;
  topicId: HelpTopicId;
  openHelp: (options?: OpenHelpOptions) => void;
  closeHelp: () => void;
  toggleHelp: () => void;
}

export const HelpContext = createContext<HelpContextValue | null>(null);
