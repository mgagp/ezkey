/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

/**
 * Medium date + short time in the app language, not the device locale (#1157).
 *
 * @param value ISO string, epoch millis, or Date
 * @param locale app language (`i18n.language`)
 * @returns the formatted value, or undefined when missing or invalid
 * @since 2026
 */
export const formatDateTime = (
  value: string | number | Date | undefined | null,
  locale: string,
): string | undefined => {
  if (value == null || value === '') {
    return undefined;
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }
  return date.toLocaleString(locale, {dateStyle: 'medium', timeStyle: 'short'});
};
