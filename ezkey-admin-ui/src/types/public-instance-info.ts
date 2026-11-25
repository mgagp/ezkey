/**
 * Response from GET /api/v1/public/instance-info (unauthenticated).
 */
export interface PublicInstanceInfo {
  authApiPublicBaseUrl: string | null;
  instanceName: string;
  instanceDescription: string | null;
  aboutUrl: string | null;
  /** Product SemVer of the backend build (#1477). */
  productVersion: string | null;
  /** 7-character git SHA of the backend image; null when not stamped (#1477). */
  gitShortSha: string | null;
}
