/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentStore
 * Description: UI-facing state store tracking the active enrollment selection.
 * Security Context: Complements the polling guidelines in docs/features/AUTH_SECURITY.md by keeping enrollment
 *                   selection explicit and avoiding implicit reuse of proof tokens.
 * @since 2025
 */

import {create} from 'zustand';

type EnrollmentStore = {
  selectedId?: string;
  setSelected: (enrollmentId: string) => void;
  clear: () => void;
};

/**
 * Global Zustand store that tracks the currently selected enrollment for navigation-aware flows.
 *
 * @since 2025
 */
export const useEnrollmentStore = create<EnrollmentStore>(set => ({
  selectedId: undefined,
  setSelected: enrollmentId => set({selectedId: enrollmentId}),
  clear: () => set({selectedId: undefined}),
}));
