/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentStore
 * Description: UI-facing state store tracking the active enrollment selection and volatile per-enrollment auth summaries.
 * Security Context: Complements the polling guidelines in docs/features/AUTH_SECURITY.md by keeping enrollment
 *                   selection explicit and avoiding implicit reuse of proof tokens.
 * @since 2025
 */

import {create} from 'zustand';
import type {RecentAuthResult} from '../services/pendingAuth/types';

type EnrollmentStore = {
  selectedId?: string;
  recentAuthResults: Record<string, RecentAuthResult | undefined>;
  setSelected: (enrollmentId: string) => void;
  setRecentAuthResult: (enrollmentId: string, result: RecentAuthResult) => void;
  clear: () => void;
};

/**
 * Global Zustand store that tracks the currently selected enrollment for navigation-aware flows
 * and the latest non-durable verified auth result shown on Enrollment Detail.
 *
 * @since 2025
 */
export const useEnrollmentStore = create<EnrollmentStore>(set => ({
  selectedId: undefined,
  recentAuthResults: {},
  setSelected: enrollmentId => set({selectedId: enrollmentId}),
  setRecentAuthResult: (enrollmentId, result) =>
    set(state => ({
      recentAuthResults: {
        ...state.recentAuthResults,
        [enrollmentId]: result,
      },
    })),
  clear: () => set({selectedId: undefined, recentAuthResults: {}}),
}));
