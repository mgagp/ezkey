/**
 * Response from GET /api/v1/public/instance-info (unauthenticated).
 */
export interface PublicInstanceInfo {
  authApiPublicBaseUrl: string | null;
  instanceName: string;
  instanceDescription: string | null;
  aboutUrl: string | null;
}
