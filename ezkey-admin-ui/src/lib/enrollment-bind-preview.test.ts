import { describe, expect, it } from 'vitest';
import { showsEnrollmentQrCard, showsIntegratedDeliveryNotice } from './enrollment-bind-preview';

// #1313 test P9: with the backend change alone, an admin enrollment record shows no QR card and no
// integrated-delivery notice; ordinary enrollments keep them.
describe('enrollment bind preview', () => {
  it('shows the QR card on an ordinary enrollment at CREATED only', () => {
    expect(showsEnrollmentQrCard({ enrollmentStatus: 'CREATED', isSystemIntegration: false })).toBe(true);
    expect(showsEnrollmentQrCard({ enrollmentStatus: 'BOUND', isSystemIntegration: false })).toBe(false);
    expect(showsEnrollmentQrCard({ enrollmentStatus: 'VERIFIED', isSystemIntegration: false })).toBe(false);
  });

  it('never shows the QR card on an admin enrollment', () => {
    expect(showsEnrollmentQrCard({ enrollmentStatus: 'CREATED', isSystemIntegration: true })).toBe(false);
  });

  it('keeps the QR card when the integration flag is unknown', () => {
    expect(showsEnrollmentQrCard({ enrollmentStatus: 'CREATED' })).toBe(true);
  });

  it('shows the delivery notice on an ordinary enrollment at CREATED or BOUND', () => {
    expect(showsIntegratedDeliveryNotice({ enrollmentStatus: 'CREATED', isSystemIntegration: false })).toBe(true);
    expect(showsIntegratedDeliveryNotice({ enrollmentStatus: 'BOUND', isSystemIntegration: false })).toBe(true);
    expect(showsIntegratedDeliveryNotice({ enrollmentStatus: 'VERIFIED', isSystemIntegration: false })).toBe(false);
  });

  it('never shows the delivery notice on an admin enrollment', () => {
    expect(showsIntegratedDeliveryNotice({ enrollmentStatus: 'CREATED', isSystemIntegration: true })).toBe(false);
    expect(showsIntegratedDeliveryNotice({ enrollmentStatus: 'BOUND', isSystemIntegration: true })).toBe(false);
  });
});
