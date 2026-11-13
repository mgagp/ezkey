/**
 * Build-time identity for Admin UI chrome (Public alpha · short SHA).
 * Injected by Vite as `import.meta.env.VITE_GIT_SHA` — see
 * `docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md`.
 */

/**
 * Short SHA (≤7 chars) for footer chrome; local fallback `dev`.
 *
 * @param raw optional override (tests); defaults to Vite-injected `VITE_GIT_SHA`
 */
export function getBuildShortSha(
  raw: string | undefined = import.meta.env.VITE_GIT_SHA as string | undefined,
): string {
  const trimmed = raw?.trim();
  if (!trimmed) {
    return 'dev';
  }
  return trimmed.length > 7 ? trimmed.slice(0, 7) : trimmed;
}
