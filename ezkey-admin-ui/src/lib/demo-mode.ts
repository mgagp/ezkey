/**
 * Developer/demo mode: when enabled via VITE_DEMO_MODE=true (e.g. in .env.development),
 * allows one-touch injection of demo data into create forms. All code and presets guarded
 * by isDemoMode are stripped from production builds via vite define.
 */

import type { TFunction } from 'i18next';

export const isDemoMode = import.meta.env.VITE_DEMO_MODE === 'true';

/** Tenant preset id whose label and display strings come from the `demo` i18n namespace. */
export const UNICORN_FARM_TENANT_PRESET_ID = 'unicorn-farm';

/**
 * Applies locale-specific strings for presets that support i18n (e.g. Unicorn Farm FR/EN).
 * Garage du coin and ACME Corp presets are returned unchanged.
 */
export function resolveTenantDemoPresetForLocale(preset: TenantDemoPreset, t: TFunction): TenantDemoPreset {
  if (preset.id !== UNICORN_FARM_TENANT_PRESET_ID) return preset;
  return {
    ...preset,
    label: t('tenantPresets.unicornFarm.label', { ns: 'demo' }),
    values: {
      ...preset.values,
      tenantName: t('tenantPresets.unicornFarm.tenantName', { ns: 'demo' }),
      tenantDescription: t('tenantPresets.unicornFarm.tenantDescription', { ns: 'demo' }),
      organizationName: t('tenantPresets.unicornFarm.organizationName', { ns: 'demo' }),
    },
  };
}

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
    primaryContactPhoneNumber: string;
  };
}

export const tenantDemoPresets: TenantDemoPreset[] = isDemoMode
  ? [
  {
    id: 'garage',
    label: 'Garage du coin',
    values: {
      tenantName: 'Garage du coin',
      tenantDescription: 'Porsche, Mercedes, Audi — région de Montréal',
      organizationName: 'Garage du coin (Porsche, Mercedes, Audi)',
      organizationDomain: 'garageducoin.ca',
      countryCode: 'CA',
      timezone: 'America/Montreal',
      primaryContactName: 'Oscar Boulon',
      primaryContactEmail: 'oscar@garageducoin.ca',
      primaryContactPhoneNumber: '+15145551001',
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
      primaryContactPhoneNumber: '+15145551002',
    },
  },
  {
    id: 'unicorn-farm',
    label: 'La Ferme des Licornes',
    values: {
      tenantName: 'La Ferme des Licornes',
      tenantDescription:
        'Élevage, formation et expériences licornes — chaque unité gère ses activités',
      organizationName: 'La Ferme des Licornes',
      organizationDomain: 'fermedeslicornes.example.com',
      countryCode: 'CA',
      timezone: 'America/Montreal',
      primaryContactName: 'Élodie Martin',
      primaryContactEmail: 'elodie.martin@fermedeslicornes.example.com',
      primaryContactPhoneNumber: '+15145551003',
    },
  },
]
  : [];

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

export const integrationDemoPresets: IntegrationDemoPreset[] = isDemoMode
  ? [
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
  {
    id: 'unicorn-ride-booking',
    label: 'Unicorn Ride Booking',
    values: {
      code: 'unicorn-ride-booking',
      name: 'Unicorn Ride Booking',
      description: 'Customer-facing app for booking unicorn ride experiences',
    },
  },
  {
    id: 'unicorn-breeding-care',
    label: 'Unicorn Breeding & Care',
    values: {
      code: 'unicorn-breeding-care',
      name: 'Unicorn Breeding & Care System',
      description: 'Internal system for caretakers — health, training, and availability',
    },
  },
]
  : [];

/**
 * Applies locale-specific strings for Unicorn Farm integration presets; other presets unchanged.
 */
export function resolveIntegrationDemoPresetForLocale(
  preset: IntegrationDemoPreset,
  t: TFunction,
): IntegrationDemoPreset {
  if (preset.id === 'unicorn-ride-booking') {
    return {
      ...preset,
      label: t('integrationPresets.unicornRideBooking.label', { ns: 'demo' }),
      values: {
        ...preset.values,
        name: t('integrationPresets.unicornRideBooking.name', { ns: 'demo' }),
        description: t('integrationPresets.unicornRideBooking.description', { ns: 'demo' }),
      },
    };
  }
  if (preset.id === 'unicorn-breeding-care') {
    return {
      ...preset,
      label: t('integrationPresets.unicornBreedingCare.label', { ns: 'demo' }),
      values: {
        ...preset.values,
        name: t('integrationPresets.unicornBreedingCare.name', { ns: 'demo' }),
        description: t('integrationPresets.unicornBreedingCare.description', { ns: 'demo' }),
      },
    };
  }
  return preset;
}

// ── Enrollment create form: partial (integrationId stays from form/context) ───────

export interface EnrollmentDemoPreset {
  id: string;
  label: string;
  values: {
    name: string;
    contactEmail: string;
      contactPhoneNumber: string;
    userIdentifier: string;
    authAttemptChallengeRequired: boolean;
  };
}

export const enrollmentDemoPresets: EnrollmentDemoPreset[] = isDemoMode
  ? [
  {
    id: 'marie',
    label: 'Marie Dupont — iPhone',
    values: {
      name: 'Marie Dupont — iPhone 15',
      contactEmail: 'user@garageducoin.ca',
      contactPhoneNumber: '+15145552001',
      userIdentifier: 'marie.dupont',
      authAttemptChallengeRequired: false,
    },
  },
  {
    id: 'jean',
    label: 'Jean Martin — Android',
    values: {
      name: 'Jean Martin — Android',
      contactEmail: 'jean.martin@garageducoin.ca',
      contactPhoneNumber: '+15145552002',
      userIdentifier: 'jean.martin',
      authAttemptChallengeRequired: true,
    },
  },
  {
    id: 'oscar',
    label: 'Oscar Boulon — iPhone (Garage du coin)',
    values: {
      name: 'Oscar Boulon — iPhone 15',
      contactEmail: 'oscar@garageducoin.ca',
      contactPhoneNumber: '+15145552003',
      userIdentifier: 'oscar.boulon',
      authAttemptChallengeRequired: false,
    },
  },
  {
    id: 'big-bird',
    label: 'Big Bird — iPhone (Sesame Street)',
    values: {
      name: 'Big Bird — iPhone 15',
      contactEmail: 'big.bird@garageducoin.ca',
      contactPhoneNumber: '+15145552004',
      userIdentifier: 'big.bird',
      authAttemptChallengeRequired: false,
    },
  },
  {
    id: 'elodie-unicorn',
    label: 'Élodie Martin — iPhone (soins)',
    values: {
      name: 'Élodie Martin — iPhone 15',
      contactEmail: 'elodie.martin@fermedeslicornes.example.com',
      contactPhoneNumber: '+15145552005',
      userIdentifier: 'elodie.martin',
      authAttemptChallengeRequired: false,
    },
  },
  {
    id: 'lucas-unicorn',
    label: 'Lucas Tremblay — Android (guide)',
    values: {
      name: 'Lucas Tremblay — Android',
      contactEmail: 'lucas.tremblay@fermedeslicornes.example.com',
      contactPhoneNumber: '+15145552006',
      userIdentifier: 'lucas.tremblay',
      authAttemptChallengeRequired: true,
    },
  },
]
  : [];

// ── Admin create form: user fields only (tenantId chosen by user for tenant admin) ─

export interface AdminDemoPreset {
  id: string;
  label: string;
  isGlobal: boolean;
  values: {
    username: string;
    email: string;
      phoneNumber: string;
    firstName: string;
    lastName: string;
  };
}

export const adminDemoPresets: AdminDemoPreset[] = isDemoMode
  ? [
  {
    id: 'global',
    label: 'Global Admin (Marie Dupont)',
    isGlobal: true,
    values: {
      username: 'marie.dupont',
      email: 'marie@garageducoin.ca',
      phoneNumber: '+15145553001',
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
      email: 'jean.martin@garageducoin.ca',
      phoneNumber: '+15145553002',
      firstName: 'Jean',
      lastName: 'Martin',
    },
  },
  {
    id: 'unicorn-tenant-admin',
    label: 'Tenant Admin (Lucas Tremblay)',
    isGlobal: false,
    values: {
      username: 'lucas.tremblay',
      email: 'lucas.tremblay@fermedeslicornes.example.com',
      phoneNumber: '+15145553003',
      firstName: 'Lucas',
      lastName: 'Tremblay',
    },
  },
]
  : [];

// ── Reason field (min 10 chars): quick-select presets for demo ─────────────────
// Used in key rotation, revoke, deactivate, toggle tenant, etc.

export interface ReasonDemoPreset {
  id: string;
  /** English text (min 10 chars); used as badge label and as value when selected. */
  en: string;
  /** French text (min 10 chars); used as badge label and as value when selected. */
  fr: string;
}

export const reasonDemoPresets: ReasonDemoPreset[] = isDemoMode
  ? [
  { id: 'routine-rotation', en: 'Routine key rotation', fr: 'Rotation de clé de routine' },
  { id: 'scheduled-rotation', en: 'Scheduled key rotation', fr: 'Rotation planifiée des clés' },
  { id: 'compliance', en: 'Compliance and audit', fr: 'Conformité et audit' },
  { id: 'security-policy', en: 'Security policy update', fr: 'Mise à jour politique de sécurité' },
  { id: 'end-of-access', en: 'End of access / offboarding', fr: 'Fin d\'accès / départ' },
  { id: 'revoked-security', en: 'Revoked for security reasons', fr: 'Révoqué pour raison de sécurité' },
]
  : [];

// ── Test Auth dialog: optional context (title + message) for demo ─────────────
// Used when testing authentication with contextual approval (Garage + Unicorn Farm presets).

export interface AuthContextDemoPreset {
  id: string;
  /** Badge label (short). */
  label: string;
  /** Context title (max 200 chars). */
  contextTitle: string;
  /** Context message (max 2000 chars). */
  contextMessage: string;
}

export const authContextDemoPresets: AuthContextDemoPreset[] = isDemoMode
  ? [
  {
    id: 'garage-service',
    label: 'Garage — Ordre de réparation',
    contextTitle: 'Validation ordre de réparation',
    contextMessage:
      'Autoriser l’ordre de réparation #2847 — Porsche Cayenne, freins et distribution. Devis 2 340 $ CAD (taxes incluses).',
  },
  {
    id: 'garage-payment',
    label: 'Garage — Paiement fournisseur',
    contextTitle: 'Paiement fournisseur',
    contextMessage:
      'Valider le virement de 8 500 $ CAD à Pièces Méga-Pneus (Montréal) pour la commande CMD-2025-089 (Garage du coin).',
  },
  {
    id: 'unicorn-ride-approval',
    label: 'Ferme — Réservation visite',
    contextTitle: 'Validation réservation expérience',
    contextMessage:
      'Autoriser la réservation VISIT-2026-042 — balade à dos de licorne, créneau 14h30, 2 visiteurs (La Ferme des Licornes).',
  },
]
  : [];
