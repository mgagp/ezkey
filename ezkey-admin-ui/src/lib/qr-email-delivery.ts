/**
 * How the Admin UI reads a QR delivery attempt (#1206 step 5). The core answers 200 with an outcome
 * once it called the connector, or a problem with a typed code when it refused before any call.
 */
export type QrEmailNotice = {
  variant: 'success' | 'warning' | 'error';
  /** i18n key under `enrollments:detail.emailDelivery`. */
  key: string;
};

export function qrEmailNoticeForOutcome(outcome: string | undefined): QrEmailNotice {
  switch (outcome) {
    case 'accepted':
      return { variant: 'success', key: 'accepted' };
    case 'unknown':
      return { variant: 'warning', key: 'unknown' };
    default:
      return { variant: 'error', key: 'rejected' };
  }
}

const REFUSAL_KEYS: Record<string, string> = {
  'connector-unavailable': 'refused.connectorUnavailable',
  'locale-not-offered': 'refused.localeNotOffered',
  'enrollment-not-created': 'refused.notCreated',
  'enrollment-expired': 'refused.expired',
  'admin-enrollment': 'refused.adminEnrollment',
  'parent-not-operational': 'refused.parentNotOperational',
  'contact-email-missing': 'refused.contactEmailMissing',
  'contact-email-invalid': 'refused.contactEmailInvalid',
  'expected-server-missing': 'refused.expectedServerMissing',
  'daily-cap-reached': 'refused.dailyCapReached',
};

export function qrEmailNoticeForRefusal(code: string | undefined): QrEmailNotice {
  return { variant: 'error', key: (code && REFUSAL_KEYS[code]) || 'refused.other' };
}

/** The operator's Admin UI language decides the email's language, `en` or `fr`. */
export function qrEmailLocale(language: string | undefined): 'en' | 'fr' {
  return language?.toLowerCase().startsWith('fr') ? 'fr' : 'en';
}
