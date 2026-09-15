/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentDisplay
 * Description: Deduped identity labels for enrollment detail and pending-auth screens.
 *              Same string must not appear as two hierarchy levels.
 * @since 2026
 */

import type {Installation} from '../services/api/types';
import {shouldShowInstallationHostHint} from './installationMetadata';

/**
 * Trims a display label and treats empty strings as absent.
 *
 * @param value Raw label from enrollment or installation metadata.
 * @return Trimmed label, or undefined when blank.
 * @since 2026
 */
export function normalizeDisplayLabel(value?: string | null): string | undefined {
  if (value == null) {
    return undefined;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

/**
 * Case-insensitive equality for two display labels.
 *
 * @param left First label.
 * @param right Second label.
 * @return True when both are present and equal ignoring case.
 * @since 2026
 */
export function isSameDisplayLabel(
  left?: string | null,
  right?: string | null,
): boolean {
  const normalizedLeft = normalizeDisplayLabel(left);
  const normalizedRight = normalizeDisplayLabel(right);
  if (!normalizedLeft || !normalizedRight) {
    return false;
  }

  return normalizedLeft.toLowerCase() === normalizedRight.toLowerCase();
}

/**
 * Returns {@code value} only when it is present and distinct from every other label.
 *
 * @param value Candidate label.
 * @param others Labels that would make {@code value} redundant.
 * @return The trimmed value, or undefined when it duplicates another label.
 * @since 2026
 */
export function distinctDisplayLabel(
  value?: string | null,
  ...others: Array<string | null | undefined>
): string | undefined {
  const normalized = normalizeDisplayLabel(value);
  if (!normalized) {
    return undefined;
  }

  if (others.some(other => isSameDisplayLabel(normalized, other))) {
    return undefined;
  }

  return normalized;
}

export type EnrollmentIdentityFields = {
  enrollmentName?: string | null;
  integrationName?: string | null;
  tenantName?: string | null;
  installation?: Pick<Installation, 'name' | 'host'> | null;
};

export type EnrollmentIdentityDisplay = {
  navTitle: string;
  heroTitle: string;
  integrationLabel?: string;
  tenantLabel?: string;
  installationContext?: string;
  showHostHint: boolean;
  host?: string;
};

/**
 * Builds a calibrated identity block for an enrollment action screen.
 *
 * Hero is the enrollment (person) when present. Installation, tenant, and
 * integration are shown only when they add a distinct name. Host is a hint
 * only when branding is generic or equal to the host — same rule as Home.
 *
 * Admin MFA enrollments are person-first from the server. Do not parse
 * leftover "Global Admin MFA - … (user)" blobs. If two admin enrollments on
 * one phone collide, unpark I-2026-09-15-mobile-admin-enrollment-account-label.
 *
 * @param enrollment Enrollment or broken-enrollment metadata.
 * @param fallbackTitle Used when no name is available.
 * @return Labels for navigation, hero, optional context lines, and host hint.
 * @since 2026
 */
export function buildEnrollmentIdentityDisplay(
  enrollment: EnrollmentIdentityFields,
  fallbackTitle: string,
): EnrollmentIdentityDisplay {
  const installationLabel = normalizeDisplayLabel(enrollment.installation?.name);
  const enrollmentLabel = normalizeDisplayLabel(enrollment.enrollmentName);
  const rawIntegration = normalizeDisplayLabel(enrollment.integrationName);
  const integrationLabel = distinctDisplayLabel(rawIntegration, installationLabel);
  const tenantLabel = distinctDisplayLabel(
    enrollment.tenantName,
    installationLabel,
    rawIntegration,
  );
  const heroTitle = enrollmentLabel ?? integrationLabel ?? installationLabel ?? fallbackTitle;
  const showHostHint = shouldShowInstallationHostHint(enrollment.installation ?? {});

  return {
    navTitle: heroTitle,
    heroTitle,
    integrationLabel: distinctDisplayLabel(integrationLabel, heroTitle),
    tenantLabel,
    installationContext: showHostHint
      ? undefined
      : distinctDisplayLabel(installationLabel, heroTitle),
    showHostHint,
    host: normalizeDisplayLabel(enrollment.installation?.host),
  };
}

export type PendingRequestTitleFields = {
  contextTitle?: string | null;
  enrollmentName?: string | null;
  integrationName?: string | null;
  installationName?: string | null;
};

/**
 * Titles for the pending approve/deny card.
 *
 * Primary is the request context, else the enrollment (person), else the
 * integration. Secondary is the person when context is the title; otherwise
 * the integration only when it differs from the installation.
 *
 * @param input Pending attempt plus local enrollment labels.
 * @return Primary and optional secondary titles.
 * @since 2026
 */
export function buildPendingRequestTitles(
  input: PendingRequestTitleFields,
): {primaryTitle?: string; secondaryTitle?: string} {
  const contextTitle = normalizeDisplayLabel(input.contextTitle);
  const enrollmentName = normalizeDisplayLabel(input.enrollmentName);
  const integrationName = normalizeDisplayLabel(input.integrationName);
  const installationName = normalizeDisplayLabel(input.installationName);
  const primaryTitle = contextTitle ?? enrollmentName ?? integrationName;

  if (!primaryTitle) {
    return {};
  }

  if (contextTitle) {
    return {
      primaryTitle,
      secondaryTitle:
        enrollmentName ??
        distinctDisplayLabel(integrationName, contextTitle, installationName),
    };
  }

  return {
    primaryTitle,
    secondaryTitle: distinctDisplayLabel(integrationName, primaryTitle, installationName),
  };
}
