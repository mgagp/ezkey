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

export type RecentAuthResult = {
  status: 'approved' | 'rejected' | 'failed';
  title: string;
  message?: string;
  completedAt: string;
};