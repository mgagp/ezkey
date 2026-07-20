/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: installationMetadata
 * Description: Trust-zone installation identity and branding derivation for enrollments
 *              that belong to a normalized Auth API URL installation.
 * @since 2025
 */

import {env} from '../config/env';
import type {
  EnrollmentSummary,
  Installation,
  PublicInstanceInfoResponse,
} from '../services/api/types';
import {normalizeInstallationId, validateAuthUrl} from './urlValidation';

const DEFAULT_INSTALLATION_NAME = 'Ezkey installation';
const STALE_AFTER_MS = 24 * 60 * 60 * 1000;
const GENERIC_INSTALLATION_NAMES = new Set(['ezkey installation', 'ezkey system']);

type LegacyInstallationSummary = {
  installationId?: string;
  installationHost?: string;
  installationName?: string;
  installationDescription?: string;
  installationAboutUrl?: string;
  installationLastRefreshedAt?: string;
};

type InstallationCarrier = LegacyInstallationSummary & {
  id?: string;
  authUrl?: string;
  installation?: Partial<Installation>;
};

function normalizeText(value: string | null | undefined): string | undefined {
  if (value == null) {
    return undefined;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

export function resolveEnrollmentAuthUrl(authUrl?: string): string | undefined {
  return validateAuthUrl(authUrl) ?? validateAuthUrl(env.configuredApiBaseUrl);
}

export function getInstallationHost(authUrl?: string): string | undefined {
  const normalized = normalizeInstallationId(authUrl);
  if (!normalized) {
    return undefined;
  }

  return normalized.replace(/^https?:\/\//, '').split('/')[0];
}

function normalizeInstallation(input?: Partial<Installation>): Partial<Installation> {
  if (!input) {
    return {};
  }

  return {
    id: normalizeText(input.id),
    authUrl: validateAuthUrl(input.authUrl),
    host: normalizeText(input.host),
    name: normalizeText(input.name),
    description: normalizeText(input.description),
    aboutUrl: normalizeText(input.aboutUrl),
    lastRefreshedAt: normalizeText(input.lastRefreshedAt),
  };
}

/**
 * Resolves the trust-zone id for an enrollment carrier.
 *
 * Never substitutes the local enrollment `id` for installation identity — that
 * would collapse distinct trust zones onto server enrollment ids.
 *
 * @param record Enrollment or legacy carrier with optional installation fields.
 * @return Normalized installation id, or undefined when the trust zone is unknown.
 * @since 2026
 */
export function resolveEnrollmentTrustZoneId(
  record: InstallationCarrier,
): string | undefined {
  const normalizedInstallation = normalizeInstallation(record.installation);
  const effectiveAuthUrl = resolveEnrollmentAuthUrl(
    normalizedInstallation.authUrl ?? record.authUrl,
  );

  const fromNestedId = normalizedInstallation.id
    ? normalizeInstallationId(normalizedInstallation.id) ??
      normalizeText(normalizedInstallation.id)
    : undefined;
  if (fromNestedId) {
    return fromNestedId;
  }

  const fromLegacyId = normalizeText(record.installationId);
  if (fromLegacyId) {
    return normalizeInstallationId(fromLegacyId) ?? fromLegacyId;
  }

  return normalizeInstallationId(effectiveAuthUrl);
}

export function buildInstallation(
  authUrl: string,
  instanceInfo?: PublicInstanceInfoResponse,
  refreshedAt: string = new Date().toISOString(),
): Installation {
  const installationId = normalizeInstallationId(authUrl);
  const installationHost = getInstallationHost(authUrl);
  const installationName =
    normalizeText(instanceInfo?.instanceName) ?? installationHost ?? DEFAULT_INSTALLATION_NAME;

  return {
    id: installationId ?? authUrl,
    authUrl: validateAuthUrl(authUrl) ?? authUrl,
    host: installationHost,
    name: installationName,
    description: normalizeText(instanceInfo?.instanceDescription),
    aboutUrl: normalizeText(instanceInfo?.aboutUrl),
    lastRefreshedAt: refreshedAt,
  };
}

export const buildInstallationSummary = buildInstallation;

export function hydrateInstallationMetadata<T extends InstallationCarrier>(record: T): T {
  const normalizedInstallation = normalizeInstallation(record.installation);
  const effectiveAuthUrl = resolveEnrollmentAuthUrl(
    normalizedInstallation.authUrl ?? record.authUrl,
  );
  const installationId = resolveEnrollmentTrustZoneId(record);
  const installationHost =
    normalizedInstallation.host ??
    normalizeText(record.installationHost) ??
    getInstallationHost(effectiveAuthUrl);
  const installationName =
    normalizedInstallation.name ??
    normalizeText(record.installationName) ??
    installationHost ??
    DEFAULT_INSTALLATION_NAME;
  const installation = installationId
    ? {
        id: installationId,
        authUrl: effectiveAuthUrl ?? normalizedInstallation.authUrl,
        host: installationHost,
        name: installationName,
        description:
          normalizedInstallation.description ?? normalizeText(record.installationDescription),
        aboutUrl:
          normalizedInstallation.aboutUrl ?? normalizeText(record.installationAboutUrl),
        lastRefreshedAt:
          normalizedInstallation.lastRefreshedAt ??
          normalizeText(record.installationLastRefreshedAt),
      }
    : undefined;

  return {
    ...record,
    installation,
  };
}

export function isInstallationMetadataStale(
  record: InstallationCarrier,
  now: number = Date.now(),
): boolean {
  const lastRefreshedAt =
    normalizeInstallation(record.installation).lastRefreshedAt ??
    normalizeText(record.installationLastRefreshedAt);
  if (!lastRefreshedAt) {
    return true;
  }

  const parsed = Date.parse(lastRefreshedAt);
  if (Number.isNaN(parsed)) {
    return true;
  }

  return now - parsed >= STALE_AFTER_MS;
}

export function needsInstallationMetadataRefresh(record: InstallationCarrier): boolean {
  const installation = normalizeInstallation(record.installation);
  const installationHost =
    installation.host ??
    normalizeText(record.installationHost) ??
    getInstallationHost(installation.authUrl ?? record.authUrl);
  const installationName = installation.name ?? normalizeText(record.installationName);
  const installationDescription =
    installation.description ?? normalizeText(record.installationDescription);

  if (!installationName) {
    return true;
  }

  if (installationHost && installationName.toLowerCase() === installationHost.toLowerCase()) {
    return installationDescription == null;
  }

  return false;
}

export function shouldShowInstallationHostHint(
  installation:
    | Pick<Installation, 'host' | 'name'>
    | Pick<EnrollmentSummary, 'installation'>
    | Pick<LegacyInstallationSummary, 'installationHost' | 'installationName'>,
): boolean {
  let normalizedInstallation: Partial<Installation>;

  if ('installation' in installation) {
    normalizedInstallation = normalizeInstallation(installation.installation);
  } else if ('host' in installation || 'name' in installation) {
    normalizedInstallation = normalizeInstallation(installation);
  } else {
    const legacyInstallation = installation as Pick<
      LegacyInstallationSummary,
      'installationHost' | 'installationName'
    >;
    normalizedInstallation = {
      host: normalizeText(legacyInstallation.installationHost),
      name: normalizeText(legacyInstallation.installationName),
    };
  }

  const installationHost = normalizedInstallation.host;
  const installationName = normalizedInstallation.name;
  if (!installationHost) {
    return false;
  }

  if (!installationName) {
    return true;
  }

  if (installationName.toLowerCase() === installationHost.toLowerCase()) {
    return true;
  }

  return GENERIC_INSTALLATION_NAMES.has(installationName.toLowerCase());
}
