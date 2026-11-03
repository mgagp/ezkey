/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import thirdPartyData from '../../data/thirdPartyLicenses.json';

/** Where a component comes from: bundled JS, or native code for one platform. */
export type LicenseSource = 'js' | 'android' | 'ios';

/**
 * One third-party component shipped in the app, as written by
 * `scripts/generate-third-party-licenses.mjs`.
 */
export type LicenseEntry = {
  id: string;
  name: string;
  version: string;
  license: string;
  source: LicenseSource;
  platforms: string[];
  proprietary?: boolean;
  website?: string;
  textIds?: string[];
  termsUrls?: string[];
};

type LicenseSnapshot = {
  generatedAt?: string;
  packages: LicenseEntry[];
  texts: Record<string, string>;
};

const snapshot = thirdPartyData as LicenseSnapshot;

/** Snapshot date (YYYY-MM-DD), or an empty string when the snapshot has none. */
export const LICENSE_SNAPSHOT_DATE = snapshot.generatedAt ? snapshot.generatedAt.slice(0, 10) : '';

/** Grouped entries for one platform; empty groups are left for the screen to hide. */
export type LicenseGroups = {
  app: LicenseEntry[];
  native: LicenseEntry[];
  vendor: LicenseEntry[];
};

/**
 * Groups the components shipped on a platform: bundled JS, open source native
 * libraries, and vendor components under proprietary terms.
 *
 * @param platform the running platform
 * @returns the three groups, each sorted as in the snapshot
 */
export function licenseGroupsFor(platform: 'android' | 'ios'): LicenseGroups {
  const app = snapshot.packages.filter(
    entry => entry.source === 'js' && entry.platforms.includes(platform),
  );
  const native = snapshot.packages.filter(entry => entry.source === platform);
  return {
    app,
    native: native.filter(entry => !entry.proprietary),
    vendor: native.filter(entry => entry.proprietary),
  };
}

/**
 * Looks up one component by snapshot id.
 *
 * @param id the entry id (for example `js:axios@1.21.0`)
 * @returns the entry, or undefined when the id is unknown
 */
export function getLicenseEntry(id: string): LicenseEntry | undefined {
  return snapshot.packages.find(entry => entry.id === id);
}

/**
 * Full license texts for an entry, in the order the snapshot lists them.
 *
 * @param entry the component
 * @returns the texts (empty when only a terms link exists)
 */
export function getLicenseTexts(entry: LicenseEntry): string[] {
  return (entry.textIds ?? [])
    .map(textId => snapshot.texts[textId])
    .filter((text): text is string => typeof text === 'string');
}

/**
 * Secondary label for a row: Maven coordinates for native entries, nothing for JS.
 *
 * @param entry the component
 * @returns coordinates such as `androidx.core:core`, or an empty string
 */
export function nativeCoordinates(entry: LicenseEntry): string {
  return entry.source === 'js' ? '' : entry.id.slice(entry.source.length + 1);
}
