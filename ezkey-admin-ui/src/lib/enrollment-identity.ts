import { z } from 'zod';

/**
 * Enrollment Name and User ID (#940).
 *
 * Name is the device label shown on the phone. The column is
 * enrollment_name VARCHAR(64) (schema V1).
 * User ID is the username in the protected application. The column is
 * user_identifier VARCHAR(255) (schema V5). It is not unique, and it is
 * the identifier integrations use to sign the user in.
 *
 * The update DTO allows a 255-character name, and create validates neither length.
 * The UI follows the columns. An empty User ID stays optional.
 */

/** Device label column width. */
export const ENROLLMENT_NAME_MAX_LENGTH = 64;

/** Integrating-application username column width. */
export const ENROLLMENT_USER_ID_MAX_LENGTH = 255;

/**
 * Name, then User ID, on the create form, the edit form, the detail page and the lists.
 */
export const ENROLLMENT_IDENTITY_FIELD_ORDER = ['name', 'userId'] as const;

export type EnrollmentIdentityField = (typeof ENROLLMENT_IDENTITY_FIELD_ORDER)[number];

/** True when the operator has not entered a User ID (blank counts as empty). */
export function isEnrollmentUserIdUnset(value: string | null | undefined): boolean {
  return value == null || value.trim() === '';
}

/**
 * Non-blocking warning for an empty User ID. Application integrations only.
 * The system integration is administrator MFA: Demo Login, PAM and the other
 * integrations do not sign that person in by this field.
 */
export function showsEmptyEnrollmentUserIdWarning(input: {
  userIdentifier: string | null | undefined;
  isSystemIntegration?: boolean | null;
}): boolean {
  if (input.isSystemIntegration === true) return false;
  return isEnrollmentUserIdUnset(input.userIdentifier);
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

/**
 * Puts the User ID column immediately after Name. Other columns keep their order.
 * Lists that already have that order are returned unchanged.
 */
export function placeEnrollmentUserIdAfterName<T extends { key: string }>(columns: T[]): T[] {
  const nameIndex = columns.findIndex((column) => column.key === 'enrollmentName');
  const userIndex = columns.findIndex((column) => column.key === 'userIdentifier');
  if (nameIndex < 0 || userIndex < 0 || userIndex === nameIndex + 1) {
    return columns;
  }
  const next = columns.slice();
  const [userColumn] = next.splice(userIndex, 1);
  const insertAt = next.findIndex((column) => column.key === 'enrollmentName') + 1;
  next.splice(insertAt, 0, userColumn);
  return next;
}
