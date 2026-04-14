/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: installationMetadata
 * Description: Canonical Ezkey installation metadata derivation for local enrollment presentation.
 * @since 2025
 */

import {env} from '../config/env';
import type {
  EnrollmentSummary,
  InstallationSummary,
  PublicInstanceInfoResponse,
} from '../services/api/types';
import {normalizeInstallationId, validateAuthUrl} from './urlValidation';

const DEFAULT_INSTALLATION_NAME = 'Ezkey installation';
const STALE_AFTER_MS = 24 * 60 * 60 * 1000;
const GENERIC_INSTALLATION_NAMES = new Set(['ezkey installation', 'ezkey system']);

type InstallationCarrier = Partial<InstallationSummary> & {
  authUrl?: string;
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

export function buildInstallationSummary(
  authUrl: string,
  instanceInfo?: PublicInstanceInfoResponse,
  refreshedAt: string = new Date().toISOString(),
): InstallationSummary {
  const installationId = normalizeInstallationId(authUrl);
  const installationHost = getInstallationHost(authUrl);
  const installationName = normalizeText(instanceInfo?.instanceName) ?? installationHost ?? DEFAULT_INSTALLATION_NAME;

  return {
    installationId,
    installationHost,
    installationName,
    installationDescription: normalizeText(instanceInfo?.instanceDescription),
    installationAboutUrl: normalizeText(instanceInfo?.aboutUrl),
    installationLastRefreshedAt: refreshedAt,
  };
}

export function hydrateInstallationMetadata<T extends InstallationCarrier>(record: T): T {
  const effectiveAuthUrl = resolveEnrollmentAuthUrl(record.authUrl);
  const installationId = record.installationId ?? normalizeInstallationId(effectiveAuthUrl);
  const installationHost = normalizeText(record.installationHost) ?? getInstallationHost(effectiveAuthUrl);
  const installationName =
    normalizeText(record.installationName) ?? installationHost ?? DEFAULT_INSTALLATION_NAME;

  return {
    ...record,
    authUrl: effectiveAuthUrl ?? record.authUrl,
    installationId,
    installationHost,
    installationName,
    installationDescription: normalizeText(record.installationDescription),
    installationAboutUrl: normalizeText(record.installationAboutUrl),
    installationLastRefreshedAt: normalizeText(record.installationLastRefreshedAt),
  };
}

export function isInstallationMetadataStale(record: InstallationCarrier, now: number = Date.now()): boolean {
  const lastRefreshedAt = normalizeText(record.installationLastRefreshedAt);
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
  const installationHost = normalizeText(record.installationHost) ?? getInstallationHost(record.authUrl);
  const installationName = normalizeText(record.installationName);
  const installationDescription = normalizeText(record.installationDescription);

  if (!installationName) {
    return true;
  }

  if (installationHost && installationName.toLowerCase() === installationHost.toLowerCase()) {
    return installationDescription == null;
  }

  return false;
}

export function shouldShowInstallationHostHint(
  installation: Pick<EnrollmentSummary, 'installationHost' | 'installationName'>,
): boolean {
  const installationHost = normalizeText(installation.installationHost);
  const installationName = normalizeText(installation.installationName);
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