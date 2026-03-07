/**
 * Country options for tenant forms (ISO 3166-1 alpha-2).
 * Codes are static; names come from Intl.DisplayNames so they stay correct and localisable.
 */

const ISO_3166_1_ALPHA2_CODES = [
  'AD', 'AE', 'AF', 'AG', 'AI', 'AL', 'AM', 'AO', 'AQ', 'AR', 'AS', 'AT', 'AU', 'AW', 'AX', 'AZ',
  'BA', 'BB', 'BD', 'BE', 'BF', 'BG', 'BH', 'BI', 'BJ', 'BL', 'BM', 'BN', 'BO', 'BQ', 'BR', 'BS',
  'BT', 'BV', 'BW', 'BY', 'BZ', 'CA', 'CC', 'CD', 'CF', 'CG', 'CH', 'CI', 'CK', 'CL', 'CM', 'CN',
  'CO', 'CR', 'CU', 'CV', 'CW', 'CX', 'CY', 'CZ', 'DE', 'DJ', 'DK', 'DM', 'DO', 'DZ', 'EC', 'EE',
  'EG', 'EH', 'ER', 'ES', 'ET', 'FI', 'FJ', 'FK', 'FM', 'FO', 'FR', 'GA', 'GB', 'GD', 'GE', 'GF',
  'GG', 'GH', 'GI', 'GL', 'GM', 'GN', 'GP', 'GQ', 'GR', 'GS', 'GT', 'GU', 'GW', 'GY', 'HK', 'HM',
  'HN', 'HR', 'HT', 'HU', 'ID', 'IE', 'IL', 'IM', 'IN', 'IO', 'IQ', 'IR', 'IS', 'IT', 'JE', 'JM',
  'JO', 'JP', 'KE', 'KG', 'KH', 'KI', 'KM', 'KN', 'KP', 'KR', 'KW', 'KY', 'KZ', 'LA', 'LB', 'LC',
  'LI', 'LK', 'LR', 'LS', 'LT', 'LU', 'LV', 'LY', 'MA', 'MC', 'MD', 'ME', 'MF', 'MG', 'MH', 'MK',
  'ML', 'MM', 'MN', 'MO', 'MP', 'MQ', 'MR', 'MS', 'MT', 'MU', 'MV', 'MW', 'MX', 'MY', 'MZ', 'NA',
  'NC', 'NE', 'NF', 'NG', 'NI', 'NL', 'NO', 'NP', 'NR', 'NU', 'NZ', 'OM', 'PA', 'PE', 'PF', 'PG',
  'PH', 'PK', 'PL', 'PM', 'PN', 'PR', 'PS', 'PT', 'PW', 'PY', 'QA', 'RE', 'RO', 'RS', 'RU', 'RW',
  'SA', 'SB', 'SC', 'SD', 'SE', 'SG', 'SH', 'SI', 'SJ', 'SK', 'SL', 'SM', 'SN', 'SO', 'SR', 'SS',
  'ST', 'SV', 'SX', 'SY', 'SZ', 'TC', 'TD', 'TF', 'TG', 'TH', 'TJ', 'TK', 'TL', 'TM', 'TN', 'TO',
  'TR', 'TT', 'TV', 'TW', 'TZ', 'UA', 'UG', 'UM', 'US', 'UY', 'UZ', 'VA', 'VC', 'VE', 'VG', 'VI',
  'VN', 'VU', 'WF', 'WS', 'YE', 'YT', 'ZA', 'ZM', 'ZW',
] as const;

let cachedOptions: { code: string; name: string }[] | null = null;

/**
 * Returns country options for dropdowns: ISO 3166-1 alpha-2 code + English name.
 * Uses Intl.DisplayNames so names are standard and no need to ship a names list.
 */
export function getCountryOptions(): { code: string; name: string }[] {
  if (cachedOptions !== null) return cachedOptions;
  const displayNames = new Intl.DisplayNames(['en'], { type: 'region' });
  cachedOptions = ISO_3166_1_ALPHA2_CODES.map((code) => ({
    code,
    name: displayNames.of(code) ?? code,
  }));
  return cachedOptions;
}

/** Quick-access country codes: North America. */
const QUICK_NA_CODES = ['CA', 'US'] as const;

/** Quick-access country codes: Europe (France & nearby), short list. */
const QUICK_EUROPE_CODES = ['FR', 'BE', 'LU', 'CH', 'DE', 'GB'] as const;

export type CountryOptionsGrouped = {
  quickNorthAmerica: { code: string; name: string }[];
  quickEurope: { code: string; name: string }[];
  all: { code: string; name: string }[];
};

/**
 * Returns country options grouped for quick-access optgroups (North America, Europe) then all.
 */
export function getCountryOptionsGrouped(): CountryOptionsGrouped {
  const all = getCountryOptions();
  const byCode = new Map(all.map((o) => [o.code, o]));
  return {
    quickNorthAmerica: QUICK_NA_CODES.map((code) => ({ code, name: byCode.get(code)?.name ?? code })),
    quickEurope: QUICK_EUROPE_CODES.map((code) => ({ code, name: byCode.get(code)?.name ?? code })),
    all,
  };
}
