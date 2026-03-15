/**
 * Developer/demo mode: when enabled via VITE_DEMO_MODE=true (e.g. in .env.development),
 * allows one-touch injection of demo data into create forms. All code and presets guarded
 * by isDemoMode are stripped from production builds via vite define.
 */

export const isDemoMode = import.meta.env.VITE_DEMO_MODE === 'true';

// ── Tenant create form (CreateFormValues in tenants.tsx) ────────────────────────

export interface TenantDemoPreset {
  id: string;
  label: string;
  values: {
    tenantName: string;
    tenantDescription: string;
    organizationName: string;
    organizationDomain: string;
    countryCode: string;
    timezone: string;
    primaryContactName: string;
    primaryContactEmail: string;
  };
}

export const tenantDemoPresets: TenantDemoPreset[] = [
  {
    id: 'garage',
    label: 'Garage du coin',
    values: {
      tenantName: 'Garage du coin',
      tenantDescription: 'Porsche, Mercedes, Audi',
      organizationName: 'Garage du coin (Porsche, Mercedes, Audi)',
      organizationDomain: 'garageducoin.com',
      countryCode: 'FR',
      timezone: 'Europe/Paris',
      primaryContactName: 'Oscar Dupont',
      primaryContactEmail: 'oscar@garageducoin.com',
    },
  },
  {
    id: 'acme',
    label: 'ACME Corp',
    values: {
      tenantName: 'ACME Corp',
      tenantDescription: 'Internal tools and customer portals',
      organizationName: 'ACME Corporation',
      organizationDomain: 'acme.example.com',
      countryCode: 'US',
      timezone: 'America/New_York',
      primaryContactName: 'Jane Smith',
      primaryContactEmail: 'jane.smith@acme.example.com',
    },
  },
];

// ── Integration create form (CreateFormValues in integrations.tsx) ───────────────

export interface IntegrationDemoPreset {
  id: string;
  label: string;
  values: {
    code: string;
    name: string;
    description: string;
  };
}

export const integrationDemoPresets: IntegrationDemoPreset[] = [
  {
    id: 'admin-console',
    label: 'Admin Console',
    values: {
      code: 'admin-console',
      name: 'Administration',
      description: "Console d'administration",
    },
  },
  {
    id: 'internal-tools',
    label: 'Internal Tools',
    values: {
      code: 'internal-tools',
      name: 'Internal Tools',
      description: 'Internal applications and dashboards',
    },
  },
];

// ── Enrollment create form: partial (integrationId stays from form/context) ───────

export interface EnrollmentDemoPreset {
  id: string;
  label: string;
  values: {
    name: string;
    contactEmail: string;
    userIdentifier: string;
    authAttemptChallengeRequired: boolean;
  };
}

export const enrollmentDemoPresets: EnrollmentDemoPreset[] = [
  {
    id: 'marie',
    label: 'Marie Dupont — iPhone',
    values: {
      name: 'Marie Dupont — iPhone 15',
      contactEmail: 'user@garageducoin.com',
      userIdentifier: 'marie.dupont',
      authAttemptChallengeRequired: false,
    },
  },
  {
    id: 'jean',
    label: 'Jean Martin — Android',
    values: {
      name: 'Jean Martin — Android',
      contactEmail: 'jean.martin@garageducoin.com',
      userIdentifier: 'jean.martin',
      authAttemptChallengeRequired: true,
    },
  },
];

// ── Admin create form: user fields only (tenantId chosen by user for tenant admin) ─

export interface AdminDemoPreset {
  id: string;
  label: string;
  isGlobal: boolean;
  values: {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
  };
}

export const adminDemoPresets: AdminDemoPreset[] = [
  {
    id: 'global',
    label: 'Global Admin (Marie Dupont)',
    isGlobal: true,
    values: {
      username: 'marie.dupont',
      email: 'marie@garageducoin.com',
      firstName: 'Marie',
      lastName: 'Dupont',
    },
  },
  {
    id: 'tenant',
    label: 'Tenant Admin (Jean Martin)',
    isGlobal: false,
    values: {
      username: 'jean.martin',
      email: 'jean.martin@garageducoin.com',
      firstName: 'Jean',
      lastName: 'Martin',
    },
  },
];

// ── Reason field (min 10 chars): quick-select presets for demo ─────────────────
// Used in key rotation, revoke, deactivate, toggle tenant, etc.

export interface ReasonDemoPreset {
  id: string;
  /** English text (min 10 chars); used as badge label and as value when selected. */
  en: string;
  /** French text (min 10 chars); used as badge label and as value when selected. */
  fr: string;
}

export const reasonDemoPresets: ReasonDemoPreset[] = [
  { id: 'routine-rotation', en: 'Routine key rotation', fr: 'Rotation de clé de routine' },
  { id: 'scheduled-rotation', en: 'Scheduled key rotation', fr: 'Rotation planifiée des clés' },
  { id: 'compliance', en: 'Compliance and audit', fr: 'Conformité et audit' },
  { id: 'security-policy', en: 'Security policy update', fr: 'Mise à jour politique de sécurité' },
  { id: 'end-of-access', en: 'End of access / offboarding', fr: 'Fin d\'accès / départ' },
  { id: 'revoked-security', en: 'Revoked for security reasons', fr: 'Révoqué pour raison de sécurité' },
];
