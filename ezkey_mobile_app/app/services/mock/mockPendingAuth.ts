/**
 * Mock pending auth attempt for Phase 1 UI development.
 */

export type MockPendingAttempt = {
  attemptId: string;
  integrationName: string;
  contextTitle?: string;
  contextMessage?: string;
  requestedAt: string;
  requiresTwoDigitChallenge?: boolean;
};

export const MOCK_PENDING_ATTEMPT: MockPendingAttempt = {
  attemptId: '501',
  integrationName: 'Acme Corp Admin',
  contextTitle: 'Sign in to Admin Console',
  contextMessage: 'Please approve this sign-in request from your device.',
  requestedAt: new Date().toISOString(),
  requiresTwoDigitChallenge: true,
};
