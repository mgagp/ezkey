export type PendingAttempt = {
  authAttemptId: string;
  authAttemptProofToken: string;
  authAttemptProofTokenSignedByIntegration: string;
  integrationName: string;
  tenantName?: string;
  createdAt: string;
  challengeRequired: boolean;
  contextTitle?: string;
  contextMessage?: string;
};