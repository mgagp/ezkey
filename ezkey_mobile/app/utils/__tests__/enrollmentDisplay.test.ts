/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import {
  buildEnrollmentIdentityDisplay,
  buildHomeCardLabels,
  buildPendingRequestTitles,
  distinctDisplayLabel,
  shouldShowHomeTenantSection,
} from '../enrollmentDisplay';

const copy = {
  administrationPurpose: 'Administration',
  globalAdminRole: 'Global Admin',
  tenantAdminRole: 'Tenant Admin',
};

describe('distinctDisplayLabel', () => {
  it('hides a label that matches another level', () => {
    expect(distinctDisplayLabel('Unicorn Farm', 'unicorn farm')).toBeUndefined();
  });

  it('keeps a label that adds information', () => {
    expect(distinctDisplayLabel('Ride Booking', 'Unicorn Farm')).toBe('Ride Booking');
  });
});

describe('regular integration user', () => {
  const regular = {
    enrollmentName: 'Jean',
    integrationName: 'Ride Booking',
    tenantName: 'Unicorn Farm',
    installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
  };

  it('uses account as hero and Ride Booking as purpose', () => {
    const display = buildEnrollmentIdentityDisplay(regular, 'Ezkey installation', copy);

    expect(display.heroTitle).toBe('Jean');
    expect(display.purposeLabel).toBe('Ride Booking');
    expect(display.roleLabel).toBeUndefined();
    expect(display.tenantLabel).toBeUndefined();
    expect(display.installationContext).toBe('Unicorn Farm');
  });

  it('titles the Home card with purpose and subtitles the account', () => {
    expect(buildHomeCardLabels(regular, 'Ezkey installation', copy)).toEqual({
      title: 'Ride Booking',
      subtitle: 'Jean',
      roleLabel: undefined,
    });
  });

  it('uses account as pending primary and purpose as secondary', () => {
    expect(
      buildPendingRequestTitles({
        enrollmentName: 'Jean',
        purposeLabel: 'Ride Booking',
        installationName: 'Unicorn Farm',
      }),
    ).toEqual({
      primaryTitle: 'Jean',
      secondaryTitle: 'Ride Booking',
    });
  });

  it('shows tenant name and description when they differ from the installation', () => {
    const display = buildEnrollmentIdentityDisplay(
      {
        enrollmentName: 'Zed',
        integrationName: 'admin1',
        tenantName: 'Unicorn Farm Accountability',
        tenantDescription: 'Internal admin console for unicorn ops',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
      copy,
    );

    expect(display.tenantLabel).toBe('Unicorn Farm Accountability');
    expect(display.tenantDescription).toBe('Internal admin console for unicorn ops');
    expect(display.installationContext).toBe('Unicorn Farm');
  });
});

describe('Global Admin MFA', () => {
  const globalAdmin = {
    enrollmentName: 'Andre Lalonde',
    integrationName: 'Unicorn Farm',
    tenantName: 'Unicorn Farm',
    isSystemIntegration: true,
    adminType: 'GLOBAL_ADMIN' as const,
    installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
  };

  it('shows Administration purpose and Global Admin role, never the system tenant', () => {
    const display = buildEnrollmentIdentityDisplay(globalAdmin, 'Ezkey installation', copy);

    expect(display.heroTitle).toBe('Andre Lalonde');
    expect(display.purposeLabel).toBe('Administration');
    expect(display.roleLabel).toBe('Global Admin');
    expect(display.tenantLabel).toBeUndefined();
    expect(display.integrationLabel).toBeUndefined();
  });

  it('titles the Home card Administration, not the org name', () => {
    expect(buildHomeCardLabels(globalAdmin, 'Ezkey installation', copy)).toEqual({
      title: 'Administration',
      subtitle: 'Andre Lalonde',
      roleLabel: 'Global Admin',
    });
  });
});

describe('Tenant Admin MFA', () => {
  const tenantAdmin = {
    enrollmentName: 'Andre Lalonde',
    integrationName: 'Unicorn Farm',
    tenantName: 'Unicorn Farm',
    isSystemIntegration: true,
    adminType: 'TENANT_ADMIN' as const,
    installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
  };

  it('uses the same purpose as Global Admin and a Tenant Admin role line', () => {
    const display = buildEnrollmentIdentityDisplay(tenantAdmin, 'Ezkey installation', copy);

    expect(display.heroTitle).toBe('Andre Lalonde');
    expect(display.purposeLabel).toBe('Administration');
    expect(display.roleLabel).toBe('Tenant Admin');
    expect(display.tenantLabel).toBeUndefined();
  });
});

describe('mix on one phone', () => {
  it('separates regular vs admin by purpose when the person name is the same', () => {
    const regular = buildHomeCardLabels(
      {
        enrollmentName: 'Andre Lalonde',
        integrationName: 'Ride Booking',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
      copy,
    );
    const admin = buildHomeCardLabels(
      {
        enrollmentName: 'Andre Lalonde',
        integrationName: 'Unicorn Farm',
        isSystemIntegration: true,
        adminType: 'GLOBAL_ADMIN',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
      copy,
    );

    expect(regular.title).toBe('Ride Booking');
    expect(admin.title).toBe('Administration');
    expect(regular.subtitle).toBe(admin.subtitle);
    expect(admin.roleLabel).toBe('Global Admin');
    expect(regular.roleLabel).toBeUndefined();
  });

  it('separates Global Admin vs Tenant Admin by role when the person name is the same', () => {
    const ga = buildHomeCardLabels(
      {
        enrollmentName: 'Andre Lalonde',
        isSystemIntegration: true,
        adminType: 'GLOBAL_ADMIN',
        integrationName: 'Unicorn Farm',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
      copy,
    );
    const ta = buildHomeCardLabels(
      {
        enrollmentName: 'Andre Lalonde',
        isSystemIntegration: true,
        adminType: 'TENANT_ADMIN',
        integrationName: 'Unicorn Farm',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
      copy,
    );

    expect(ga.title).toBe(ta.title);
    expect(ga.subtitle).toBe(ta.subtitle);
    expect(ga.roleLabel).toBe('Global Admin');
    expect(ta.roleLabel).toBe('Tenant Admin');
  });
});

describe('legacy and generic branding', () => {
  it('shows a leftover role-prefixed enrollmentName as-is without parsing', () => {
    const display = buildEnrollmentIdentityDisplay(
      {
        enrollmentName: 'Global Admin MFA - Marie Dupont (marie.dupont)',
        integrationName: 'Unicorn Farm',
        tenantName: 'Unicorn Farm',
        isSystemIntegration: true,
        adminType: 'GLOBAL_ADMIN',
        installation: {name: 'Unicorn Farm', host: 'auth.example.com'},
      },
      'Ezkey installation',
      copy,
    );

    expect(display.heroTitle).toBe('Global Admin MFA - Marie Dupont (marie.dupont)');
    expect(display.roleLabel).toBe('Global Admin');
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
      copy,
    );

    expect(display.heroTitle).toBe('Pixel 7 Pro');
    expect(display.purposeLabel).toBe('Ride Booking');
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
      copy,
    );

    expect(display.showHostHint).toBe(true);
    expect(display.host).toBe('exp1-auth-api.ezkey.org');
    expect(display.installationContext).toBeUndefined();
    expect(display.purposeLabel).toBeUndefined();
  });
});

describe('shouldShowHomeTenantSection', () => {
  it('hides a system-tenant-only group under the installation', () => {
    expect(
      shouldShowHomeTenantSection('Unicorn Farm', 'Unicorn Farm', [
        {isSystemIntegration: true},
      ]),
    ).toBe(false);
  });

  it('hides a business tenant that repeats the installation name', () => {
    expect(
      shouldShowHomeTenantSection('Unicorn Farm', 'Unicorn Farm', [
        {isSystemIntegration: false},
      ]),
    ).toBe(false);
  });

  it('shows a distinct business tenant for a regular enrollment', () => {
    expect(
      shouldShowHomeTenantSection('Unicorn Farm Accountability', 'Unicorn Farm', [
        {isSystemIntegration: false},
      ]),
    ).toBe(true);
  });
});

describe('buildPendingRequestTitles', () => {
  it('prefers context, then names the enrollment as secondary', () => {
    expect(
      buildPendingRequestTitles({
        contextTitle: 'Login Request',
        enrollmentName: 'Marie Dupont',
        purposeLabel: 'Administration',
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
        purposeLabel: 'Unicorn Farm',
        installationName: 'Unicorn Farm',
      }),
    ).toEqual({
      primaryTitle: 'Marie Dupont',
      secondaryTitle: undefined,
    });
  });

  it('shows purpose as secondary only when it differs from the installation', () => {
    expect(
      buildPendingRequestTitles({
        enrollmentName: 'Jean',
        purposeLabel: 'Ride Booking',
        installationName: 'Unicorn Farm',
      }),
    ).toEqual({
      primaryTitle: 'Jean',
      secondaryTitle: 'Ride Booking',
    });
  });
});
