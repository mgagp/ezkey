import { z } from 'zod';

/**
 * Enrollment Name and User ID (#940).
 *
 * Name is the device label shown on the phone. The column is
 * enrollment_name VARCHAR(64) (schema V1).
 * User ID is the username in the protected application. The column is
 * user_identifier VARCHAR(255) (schema V5). It is not unique. An integration
 * can target an enrollment by its enrollment ID or by this User ID; only the
 * second needs it.
 *
 * The update DTO allows a 255-character name, and create validates neither length.
 * The UI follows the columns. An empty User ID stays optional.
 */

/** Device label column width. */
export const ENROLLMENT_NAME_MAX_LENGTH = 64;

/** Integrating-application username column width. */
export const ENROLLMENT_USER_ID_MAX_LENGTH = 255;

/**
 * Integration the enrollment belongs to: an application integration, the
 * system integration (administrator MFA), or none chosen yet (create form).
 */
export type EnrollmentIntegrationKind = 'application' | 'system' | 'none';

export function enrollmentIntegrationKind(
  integration: { isSystemIntegration?: boolean | null } | null | undefined,
): EnrollmentIntegrationKind {
  if (integration == null) return 'none';
  return integration.isSystemIntegration === true ? 'system' : 'application';
}

/** True when the operator has not entered a User ID (blank counts as empty). */
export function isEnrollmentUserIdUnset(value: string | null | undefined): boolean {
  return value == null || value.trim() === '';
}

/**
 * Non-blocking warning for an empty User ID, once an application integration
 * is known. Never before an integration is chosen, never on the system integration.
 */
export function showsEmptyEnrollmentUserIdWarning(
  userIdentifier: string | null | undefined,
  kind: EnrollmentIntegrationKind,
): boolean {
  return kind === 'application' && isEnrollmentUserIdUnset(userIdentifier);
}

/** Value sent on create. Blank becomes omitted so the API stores null. */
export function enrollmentUserIdForCreate(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim() ?? '';
  return trimmed === '' ? undefined : trimmed;
}

export function enrollmentNameField(requiredMessage: string, maxMessage: string) {
  return z
    .string()
    .min(1, requiredMessage)
    .max(ENROLLMENT_NAME_MAX_LENGTH, maxMessage);
}

export function enrollmentUserIdField(maxMessage: string) {
  return z
    .string()
    .max(ENROLLMENT_USER_ID_MAX_LENGTH, maxMessage)
    .optional()
    .or(z.literal(''));
}
