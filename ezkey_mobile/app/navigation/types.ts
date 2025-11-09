export type RootStackParamList = {
  Home: undefined;
  EnrollmentDetail: {enrollmentId: string};
  PendingAuth: {enrollmentId: string};
  EnrollmentWizard: {scanned?: {enrollmentId: string; enrollmentProofToken: string; language?: string}} | undefined;
  Diagnostics: undefined;
  EnrollmentScanner: undefined;
};
