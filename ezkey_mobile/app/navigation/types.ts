import type {PendingAttempt} from '../services/pendingAuth/types';

export type RootStackParamList = {
  Home: undefined;
  EnrollmentDetail: {enrollmentId: string};
  PendingAuth: {enrollmentId: string; initialAttempt?: PendingAttempt};
  EnrollmentWizard: undefined;
  Settings: undefined;
  About: undefined;
  ReleaseNotes: undefined;
  Licenses: undefined;
  DangerZone: undefined;
  Language: undefined;
};
