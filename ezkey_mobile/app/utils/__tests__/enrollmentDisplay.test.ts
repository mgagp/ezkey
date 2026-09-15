/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import {
  buildEnrollmentIdentityDisplay,
  buildPendingRequestTitles,
  distinctDisplayLabel,
} from '../enrollmentDisplay';

describe('distinctDisplayLabel', () => {
  it('hides a label that matches another level', () => {
    expect(distinctDisplayLabel('Unicorn Farm', 'unicorn farm')).toBeUndefined();
  });

  it('keeps a label that adds information', () => {
    expect(distinctDisplayLabel('Ride Booking', 'Unicorn Farm')).toBe('Ride Booking');
  });
});

describe('buildEnrollmentIdentityDisplay', () => {
  it('uses enrollment as hero and shows installation once when org names collide', () => {
    const display = buildEnrollmentIdentityDisplay(
      {
        enrollmentName: 'Marie Dupont',
        integrationName: 'Unicorn Farm',
        tenantName: 'Unicorn Farm',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
    );

    expect(display.heroTitle).toBe('Marie Dupont');
    expect(display.navTitle).toBe(display.heroTitle);
    expect(display.installationContext).toBe('Unicorn Farm');
    expect(display.integrationLabel).toBeUndefined();
    expect(display.tenantLabel).toBeUndefined();
    expect(display.showHostHint).toBe(false);
  });

  it('shows a leftover role-prefixed enrollmentName as-is without parsing', () => {
    const display = buildEnrollmentIdentityDisplay(
      {
        enrollmentName: 'Global Admin MFA - Marie Dupont (marie.dupont)',
        integrationName: 'Unicorn Farm',
        tenantName: 'Unicorn Farm',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
    );

    expect(display.heroTitle).toBe('Global Admin MFA - Marie Dupont (marie.dupont)');
  });

  it('keeps distinct integration and tenant under a branded installation', () => {
    const display = buildEnrollmentIdentityDisplay(
      {
        enrollmentName: 'Pixel 7 Pro',
        integrationName: 'Ride Booking',
        tenantName: 'Unicorn Farm',
        installation: {name: 'Acme EU', host: 'auth.acme.example'},
      },
      'Ezkey installation',
    );

    expect(display.heroTitle).toBe('Pixel 7 Pro');
    expect(display.integrationLabel).toBe('Ride Booking');
    expect(display.tenantLabel).toBe('Unicorn Farm');
    expect(display.installationContext).toBe('Acme EU');
    expect(display.showHostHint).toBe(false);
  });

  it('shows a host hint when branding fell back to the host', () => {
    const display = buildEnrollmentIdentityDisplay(
      {
        enrollmentName: 'Marie Dupont',
        integrationName: 'exp1-auth-api.ezkey.org',
        installation: {
          name: 'exp1-auth-api.ezkey.org',
          host: 'exp1-auth-api.ezkey.org',
        },
      },
      'Ezkey installation',
    );

    expect(display.showHostHint).toBe(true);
    expect(display.host).toBe('exp1-auth-api.ezkey.org');
    expect(display.installationContext).toBeUndefined();
  });
});

describe('buildPendingRequestTitles', () => {
  it('prefers context, then names the enrollment as secondary', () => {
    expect(
      buildPendingRequestTitles({
        contextTitle: 'Login Request',
        enrollmentName: 'Marie Dupont',
        integrationName: 'Unicorn Farm',
        installationName: 'Unicorn Farm',
      }),
    ).toEqual({
      primaryTitle: 'Login Request',
      secondaryTitle: 'Marie Dupont',
    });
  });

  it('uses enrollment as primary when there is no context title', () => {
    expect(
      buildPendingRequestTitles({
        enrollmentName: 'Marie Dupont',
        integrationName: 'Unicorn Farm',
        installationName: 'Unicorn Farm',
      }),
    ).toEqual({
      primaryTitle: 'Marie Dupont',
      secondaryTitle: undefined,
    });
  });

  it('shows integration as secondary only when it differs from the installation', () => {
    expect(
      buildPendingRequestTitles({
        enrollmentName: 'Jean',
        integrationName: 'Ride Booking',
        installationName: 'Unicorn Farm',
      }),
    ).toEqual({
      primaryTitle: 'Jean',
      secondaryTitle: 'Ride Booking',
    });
  });
});
