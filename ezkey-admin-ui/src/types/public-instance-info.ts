/**
 * Response from GET /api/v1/public/instance-info (unauthenticated).
 */
export interface PublicInstanceInfo {
  authApiPublicBaseUrl: string | null;
  instanceName: string;
  instanceDescription: string | null;
  aboutUrl: string | null;
  /** When true, temporary console explore fork is advertised after activation. */
  evaluatorSelfRegistrationEnabled?: boolean | null;
}
