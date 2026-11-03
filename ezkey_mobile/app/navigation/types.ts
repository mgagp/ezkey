import type {PendingAttempt} from '../services/pendingAuth/types';

export type RootStackParamList = {
  Home: undefined;
  EnrollmentDetail: {enrollmentId: string};
  PendingAuth: {enrollmentId: string; initialAttempt?: PendingAttempt};
  EnrollmentWizard: undefined;
  Settings: undefined;
  Security: undefined;
  ComingSoon: undefined;
  About: undefined;
  ReleaseNotes: undefined;
  Licenses: undefined;
  LicenseDetail: {id: string};
  DangerZone: undefined;
  Language: undefined;
  Appearance: undefined;
};
