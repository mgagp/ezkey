/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import {DEFAULT_LOCALE} from '../services/storage/localeStorage';

/** Phone language tags, refreshed when the app language is resolved. */
let deviceLocales: readonly string[] = [];

/**
 * Remembers the phone's language list so dates can keep its regional format.
 *
 * @param tags language tags in preference order, unaffected by the per-app language
 * @since 2026
 */
export const setFormattingDeviceLocales = (tags: readonly string[]): void => {
  deviceLocales = tags;
};

/**
 * Locale tag used to format dates. When the phone's language matches the app language, its
 * regional variant is kept (`fr` on a fr-CA phone formats "15 h 34", not "15:34"). A phone in
 * another language does not leak its format into the app.
 *
 * @param appLanguage rendered app language (`i18n.language`); missing before i18n init falls back to English
 * @param deviceTags phone language tags; defaults to the ones remembered at startup
 * @returns a BCP 47 tag
 * @since 2026
 */
export const localeForFormatting = (
  appLanguage: string | undefined | null,
  deviceTags: readonly string[] = deviceLocales,
): string => {
  const language = appLanguage?.trim() ? appLanguage : DEFAULT_LOCALE;
  const primary = language.split(/[-_]/)[0]?.toLowerCase() ?? language;
  const match = deviceTags.find(tag => tag.split(/[-_]/)[0]?.toLowerCase() === primary);
  return match ?? language;
};

/**
 * Medium date + short time in the app language (#1157), keeping the phone's regional variant
 * when it matches that language.
 *
 * @param value ISO string, epoch millis, or Date
 * @param locale app language (`i18n.language`); missing before i18n init falls back to English
 * @returns the formatted value, or undefined when missing or invalid
 * @since 2026
 */
export const formatDateTime = (
  value: string | number | Date | undefined | null,
  locale: string | undefined | null,
): string | undefined => {
  if (value == null || value === '') {
    return undefined;
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }
  return date.toLocaleString(localeForFormatting(locale), {dateStyle: 'medium', timeStyle: 'short'});
};
