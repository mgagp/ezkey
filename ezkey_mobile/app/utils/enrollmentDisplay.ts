/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentDisplay
 * Description: One identity grid for every enrollment screen. Same string must not
 *              appear as two hierarchy levels. Vocabulary: Installation, Purpose,
 *              Account, Role, Device — see ezkey_mobile/docs/MOBILE_POSITIONING.md.
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

export type EnrollmentAdminType = 'GLOBAL_ADMIN' | 'TENANT_ADMIN';

export type EnrollmentIdentityCopy = {
  administrationPurpose: string;
  globalAdminRole: string;
  tenantAdminRole: string;
};

export type EnrollmentIdentityFields = {
  enrollmentName?: string | null;
  integrationName?: string | null;
  tenantName?: string | null;
  tenantDescription?: string | null;
  installation?: Pick<Installation, 'name' | 'host'> | null;
  isSystemIntegration?: boolean | null;
  adminType?: EnrollmentAdminType | string | null;
};

export type EnrollmentIdentityDisplay = {
  navTitle: string;
  heroTitle: string;
  accountLabel?: string;
  purposeLabel?: string;
  roleLabel?: string;
  integrationLabel?: string;
  tenantLabel?: string;
  tenantDescription?: string;
  installationContext?: string;
  showHostHint: boolean;
  host?: string;
};

/**
 * Localized Purpose / Role strings for {@link buildEnrollmentIdentityDisplay}.
 *
 * @param t i18n translator.
 * @return Copy used for Administration and admin roles.
 * @since 2026
 */
export function identityDisplayCopy(t: (key: string) => string): EnrollmentIdentityCopy {
  return {
    administrationPurpose: t('identity.administration'),
    globalAdminRole: t('identity.globalAdmin'),
    tenantAdminRole: t('identity.tenantAdmin'),
  };
}

/**
 * Purpose of an enrollment: localized Administration for admin MFA, otherwise
 * the integration name.
 *
 * @param enrollment Enrollment labels and admin flags.
 * @param copy Localized Administration string.
 * @return Purpose label, or undefined when absent.
 * @since 2026
 */
export function resolveEnrollmentPurpose(
  enrollment: EnrollmentIdentityFields,
  copy: EnrollmentIdentityCopy,
): string | undefined {
  if (enrollment.isSystemIntegration) {
    return normalizeDisplayLabel(copy.administrationPurpose);
  }
  return normalizeDisplayLabel(enrollment.integrationName);
}

/**
 * Localized Role for admin MFA. Hidden for regular integrations.
 *
 * @param enrollment Enrollment labels and admin flags.
 * @param copy Localized role strings.
 * @return Role label, or undefined when this is not admin MFA.
 * @since 2026
 */
export function resolveEnrollmentRole(
  enrollment: EnrollmentIdentityFields,
  copy: EnrollmentIdentityCopy,
): string | undefined {
  if (!enrollment.isSystemIntegration) {
    return undefined;
  }
  if (enrollment.adminType === 'GLOBAL_ADMIN') {
    return normalizeDisplayLabel(copy.globalAdminRole);
  }
  if (enrollment.adminType === 'TENANT_ADMIN') {
    return normalizeDisplayLabel(copy.tenantAdminRole);
  }
  return undefined;
}

/**
 * Whether Home should render a tenant section header inside an installation.
 *
 * Admin MFA belongs to the system tenant, which is not the business-org cue.
 * A tenant name that repeats the installation is the same collision rule as
 * Detail. Real-life phones usually have one hat; this header exists so a
 * regular enrollment can name its org when that org is not the instance.
 *
 * @param tenantName Tenant display name for the group.
 * @param installationName Installation / trust-zone label.
 * @param enrollments Enrollments in this tenant group.
 * @return True when the section header would add a distinct business-tenant cue.
 * @since 2026
 */
export function shouldShowHomeTenantSection(
  tenantName?: string | null,
  installationName?: string | null,
  enrollments: Array<{isSystemIntegration?: boolean | null}> = [],
): boolean {
  if (!normalizeDisplayLabel(tenantName)) {
    return false;
  }
  if (
    enrollments.length > 0 &&
    enrollments.every(enrollment => enrollment.isSystemIntegration)
  ) {
    return false;
  }
  return !isSameDisplayLabel(tenantName, installationName);
}

/**
 * Builds a calibrated identity block for an enrollment action screen.
 *
 * Hero is the Account (enrollment name / person) when present. Purpose is
 * Administration for admin MFA, otherwise the integration name when distinct
 * from the installation. Role is shown for every system-integration enrollment.
 * Host is a hint only when branding is generic or equal to the host.
 *
 * Admin MFA enrollments are person-first from the server. Do not parse
 * leftover "Global Admin MFA - … (user)" blobs.
 *
 * @param enrollment Enrollment or broken-enrollment metadata.
 * @param fallbackTitle Used when no name is available.
 * @param copy Localized Purpose and Role strings.
 * @return Labels for navigation, hero, purpose, role, optional context, and host hint.
 * @since 2026
 */
export function buildEnrollmentIdentityDisplay(
  enrollment: EnrollmentIdentityFields,
  fallbackTitle: string,
  copy: EnrollmentIdentityCopy,
): EnrollmentIdentityDisplay {
  const installationLabel = normalizeDisplayLabel(enrollment.installation?.name);
  const accountLabel = normalizeDisplayLabel(enrollment.enrollmentName);
  const rawPurpose = resolveEnrollmentPurpose(enrollment, copy);
  const purposeLabel = enrollment.isSystemIntegration
    ? rawPurpose
    : distinctDisplayLabel(rawPurpose, installationLabel);
  const roleLabel = resolveEnrollmentRole(enrollment, copy);
  const heroTitle = accountLabel ?? purposeLabel ?? installationLabel ?? fallbackTitle;
  const showHostHint = shouldShowInstallationHostHint(enrollment.installation ?? {});
  const purposeForScreen = distinctDisplayLabel(purposeLabel, heroTitle);
  const tenantLabel = enrollment.isSystemIntegration
    ? undefined
    : distinctDisplayLabel(
        enrollment.tenantName,
        installationLabel,
        rawPurpose,
        heroTitle,
      );

  return {
    navTitle: heroTitle,
    heroTitle,
    accountLabel,
    purposeLabel: purposeForScreen,
    roleLabel: distinctDisplayLabel(roleLabel, heroTitle, purposeLabel),
    integrationLabel: enrollment.isSystemIntegration
      ? undefined
      : purposeForScreen,
    tenantLabel,
    tenantDescription: tenantLabel
      ? distinctDisplayLabel(
          enrollment.tenantDescription,
          tenantLabel,
          installationLabel,
          rawPurpose,
          heroTitle,
        )
      : undefined,
    installationContext: showHostHint
      ? undefined
      : distinctDisplayLabel(installationLabel, heroTitle),
    showHostHint,
    host: normalizeDisplayLabel(enrollment.installation?.host),
  };
}

export type HomeCardLabels = {
  title: string;
  subtitle?: string;
  roleLabel?: string;
};

/**
 * Home card labels: Purpose as title, Account as subtitle, Role for admin MFA.
 *
 * @param enrollment Enrollment metadata.
 * @param fallbackTitle Used when no purpose or account is available.
 * @param copy Localized Purpose and Role strings.
 * @return Card title, optional subtitle, optional role line.
 * @since 2026
 */
export function buildHomeCardLabels(
  enrollment: EnrollmentIdentityFields,
  fallbackTitle: string,
  copy: EnrollmentIdentityCopy,
): HomeCardLabels {
  const identity = buildEnrollmentIdentityDisplay(enrollment, fallbackTitle, copy);
  const purpose =
    resolveEnrollmentPurpose(enrollment, copy) ??
    identity.heroTitle ??
    fallbackTitle;
  return {
    title: purpose,
    subtitle: distinctDisplayLabel(identity.accountLabel, purpose),
    roleLabel: identity.roleLabel,
  };
}

export type PendingRequestTitleFields = {
  contextTitle?: string | null;
  enrollmentName?: string | null;
  purposeLabel?: string | null;
  integrationName?: string | null;
  installationName?: string | null;
};

/**
 * Titles for the pending approve/deny card.
 *
 * Primary is the request context, else the Account, else Purpose. Secondary is
 * the person when context is the title; otherwise Purpose only when it differs
 * from the installation.
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
  const purposeLabel =
    normalizeDisplayLabel(input.purposeLabel) ??
    normalizeDisplayLabel(input.integrationName);
  const installationName = normalizeDisplayLabel(input.installationName);
  const primaryTitle = contextTitle ?? enrollmentName ?? purposeLabel;

  if (!primaryTitle) {
    return {};
  }

  if (contextTitle) {
    return {
      primaryTitle,
      secondaryTitle:
        enrollmentName ??
        distinctDisplayLabel(purposeLabel, contextTitle, installationName),
    };
  }

  return {
    primaryTitle,
    secondaryTitle: distinctDisplayLabel(purposeLabel, primaryTitle, installationName),
  };
}
