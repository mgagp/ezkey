import { describe, expect, it } from 'vitest';
import { qrEmailLocale, qrEmailNoticeForOutcome, qrEmailNoticeForRefusal } from './qr-email-delivery';

describe('qr email delivery', () => {
  it('reads the three outcomes', () => {
    expect(qrEmailNoticeForOutcome('accepted')).toEqual({ variant: 'success', key: 'accepted' });
    expect(qrEmailNoticeForOutcome('unknown')).toEqual({ variant: 'warning', key: 'unknown' });
    expect(qrEmailNoticeForOutcome('rejected')).toEqual({ variant: 'error', key: 'rejected' });
    expect(qrEmailNoticeForOutcome(undefined).variant).toBe('error');
  });

  it('maps known refusals and falls back for others', () => {
    expect(qrEmailNoticeForRefusal('daily-cap-reached').key).toBe('refused.dailyCapReached');
    expect(qrEmailNoticeForRefusal('contact-email-missing').key).toBe('refused.contactEmailMissing');
    expect(qrEmailNoticeForRefusal('self-registered-tenant').key).toBe('refused.selfRegisteredTenant');
    expect(qrEmailNoticeForRefusal('something-new').key).toBe('refused.other');
    expect(qrEmailNoticeForRefusal(undefined).key).toBe('refused.other');
  });

  it('picks the email language from the Admin UI language', () => {
    expect(qrEmailLocale('fr')).toBe('fr');
    expect(qrEmailLocale('fr-CA')).toBe('fr');
    expect(qrEmailLocale('en')).toBe('en');
    expect(qrEmailLocale('es')).toBe('en');
    expect(qrEmailLocale(undefined)).toBe('en');
  });
});
