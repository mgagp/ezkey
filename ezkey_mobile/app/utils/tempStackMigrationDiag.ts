import {env} from '../config/env';

/**
 * TEMP (#177 stack modernization): gated diagnostic logging for enrollment camera / Vision Camera
 * migration. Prefix {@code [EZKEY_DIAG_TEMP]} — filter with {@code adb logcat | grep EZKEY_DIAG_TEMP}.
 *
 * Removal checklist when the program closes: delete this module, {@code env.stackMigrationDiag},
 * {@code EZKEY_STACK_MIGRATION_DIAG} in {@code .env.example}, call sites in
 * {@code EnrollmentScannerModal.tsx}, Kotlin logs tagged {@code EZKEY_DIAG_TEMP}, and
 * {@code scripts/capture-stack-migration-logcat.sh}.
 */
export const EZKEY_DIAG_TEMP_TAG = 'EZKEY_DIAG_TEMP';

/**
 * Logs a migration diagnostic step when {@code EZKEY_STACK_MIGRATION_DIAG=true} (native rebuild).
 * Never pass secrets (QR payload body, tokens) — use lengths and step names only.
 *
 * @param step Short step identifier (e.g. {@code scanner.visible}).
 * @param detail Optional non-sensitive detail.
 */
export const stackMigrationDiag = (step: string, detail?: string): void => {
  if (!env.stackMigrationDiag) {
    return;
  }
  const suffix = detail ? ` | ${detail}` : '';
  console.log(`[${EZKEY_DIAG_TEMP_TAG}] ${step}${suffix}`);
};
