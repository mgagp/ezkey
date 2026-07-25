/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentStorage
 * Description: Persistence facade that splits sensitive enrollment metadata between secure storage and AsyncStorage.
 * Security Context: Implements the storage strategy described in docs/features/AUTH_SECURITY.md by isolating proof tokens
 *                   from user-friendly metadata. Uses platform Keychain for secure storage.
 * @since 2025
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import {EnrollmentSummary, Installation} from '../api/types';
import {hydrateInstallationMetadata} from '../../utils/installationMetadata';
import {nativeCrypto} from '../crypto/nativeCrypto';
import {
  DEFAULT_ENROLLMENT_APPROVAL_POLICY,
  EnrollmentApprovalPolicy,
  normalizeEnrollmentApprovalPolicy,
} from '../security/approvalRequirement';
import {secureStorage} from './secureStorage';
import type {SecurityLevel} from './securityPreferenceStorage';

const ENROLLMENT_COLLECTION_KEY = 'ezkey-mobile/enrollments';
const ENROLLMENT_PROOF_TOKEN_KEY_PREFIX = 'ezkey-mobile/enrollment-proof-token';
const INTEGRATION_PUBLIC_KEY_KEY_PREFIX = 'ezkey-mobile/integration-public-key';
/** Mirror of the sealed-secret AsyncStorage prefix owned by secureStorage (clear-all sweep only). */
const SEALED_SECRET_KEY_PREFIX = 'ezkey-mobile/sealed-secret';

/**
 * Local representation of enrollment records including proof tokens.
 *
 * With EC P-256, device keys are stored through the native platform keystore path per
 * installation-scoped local enrollment id. Installation routing and branding metadata live in the
 * nested `installation` object; legacy flattened installation fields are normalized on read.
 *
 * @since 2025
 */
export type StoredEnrollment = EnrollmentSummary & {
  enrollmentProofToken: string;
  /**
   * Auth API / DB enrollment id for the installation trust zone.
   * Wire bodies use this value; {@link EnrollmentSummary.id} is the local handle
   * (installation-scoped) used for Keystore aliases, seal keys, and navigation.
   */
  enrollmentId?: string;
  integrationPublicKey?: string;
  integrationDescription?: string;
  enrollmentName?: string;
  deviceLabel?: string;
  approvalPolicy?: EnrollmentApprovalPolicy;
  securityLevel?: SecurityLevel;
};

/**
 * Enrollment metadata as persisted in AsyncStorage: display-safe fields without rehydrated secrets.
 *
 * Also the record shape handed to the UI for enrollments whose secrets are unusable — those rows
 * must stay visible per the MOB-015 locked UI contract instead of silently disappearing.
 *
 * @since 2026
 */
export type EnrollmentMetadataRecord = Omit<StoredEnrollment, 'enrollmentProofToken'> & {
  enrollmentProofToken?: string;
  integrationPublicKey?: string;
};

type PersistedEnrollmentMetadata = EnrollmentMetadataRecord;

/**
 * Internal diagnostic reason for an unusable enrollment row.
 *
 * Production UI collapses every reason into a single "unusable on this device" state; the
 * discrimination exists for logs, `__DEV__`, and tests only (MOB-015 locked UI contract).
 *
 * @since 2026
 */
export type BrokenEnrollmentReason =
  | 'missing_proof_token'
  | 'missing_integration_public_key'
  | 'secret_rehydration_failed';

/**
 * Descriptor for a persisted enrollment whose local secrets could not be rehydrated.
 *
 * @since 2026
 */
export type BrokenEnrollment = {
  id: string;
  reason: BrokenEnrollmentReason;
  metadata: EnrollmentMetadataRecord;
};

/**
 * Discriminated listing result: local storage/crypto failure is never presented as a healthy
 * empty list (MOB-015).
 *
 * @since 2026
 */
export type EnrollmentListResult = {
  enrollments: StoredEnrollment[];
  broken: BrokenEnrollment[];
  /** True when the whole enrollment collection payload is unreadable (corrupt JSON). */
  collectionError: boolean;
};

type StorageDelegate = {
  setItem(key: string, value: string): Promise<void>;
  getItem(key: string): Promise<string | undefined>;
  removeItem(key: string): Promise<void>;
};

type EnrollmentStorageOptions = {
  secure: StorageDelegate;
  metadata: typeof AsyncStorage;
};

/**
 * Storage gateway in charge of persisting enrollment information on device.
 *
 * Persists enrollment metadata in AsyncStorage and proof tokens through the platform secure-storage delegate.
 *
 * @since 2025
 */
class EnrollmentStorage {
  private secure: StorageDelegate;
  private metadata: typeof AsyncStorage;

  constructor(options: EnrollmentStorageOptions) {
    this.secure = options.secure;
    this.metadata = options.metadata;
  }

  /**
   * Factory used to instantiate the storage module with platform secure storage.
   *
   * @return Enrollment storage instance using AsyncStorage plus the platform secure-storage delegate.
   * @since 2025
   */
  static create() {
    return new EnrollmentStorage({secure: secureStorage, metadata: AsyncStorage});
  }

  private proofTokenStorageKey(id: string) {
    return `${ENROLLMENT_PROOF_TOKEN_KEY_PREFIX}.${id}`;
  }

  private integrationPublicKeyStorageKey(id: string) {
    return `${INTEGRATION_PUBLIC_KEY_KEY_PREFIX}.${id}`;
  }

  /**
   * Best-effort removal of the per-enrollment EC key pair from the native keystore.
   *
   * Fail-open: Keystore delete failures are logged and do not block metadata or sealed-secret
   * cleanup. The user already initiated a local wipe; residual key material is worse than a
   * blocked wipe when the native call fails.
   *
   * @param enrollmentId Enrollment identifier whose keystore alias should be deleted.
   */
  private async deleteKeyPairBestEffort(enrollmentId: string): Promise<void> {
    try {
      await nativeCrypto.deleteKeyPair(enrollmentId);
    } catch (error) {
      console.warn(
        '[enrollmentStorage] Failed to delete enrollment key pair (continuing wipe):',
        enrollmentId,
        error,
      );
    }
  }

  private stripSensitiveFields(record: PersistedEnrollmentMetadata): PersistedEnrollmentMetadata {
    const {
      enrollmentProofToken: _enrollmentProofToken,
      integrationPublicKey: _integrationPublicKey,
      securityLevel: _legacySecurityLevel,
      ...metadata
    } = record;
    return metadata;
  }

  private toBrokenEnrollment(
    record: PersistedEnrollmentMetadata,
    reason: BrokenEnrollmentReason,
  ): BrokenEnrollment {
    return {
      id: record.id,
      reason,
      metadata: {
        ...this.stripSensitiveFields(record),
        approvalPolicy: normalizeEnrollmentApprovalPolicy(record.approvalPolicy),
      },
    };
  }

  /**
   * Reads and parses the raw persisted metadata collection for write paths.
   *
   * Unlike {@link listEnrollmentsDetailed}, no secret rehydration happens here, so rows whose
   * secrets are currently unusable are preserved by mutations instead of being silently dropped.
   *
   * @return Persisted metadata records; empty array when absent or unreadable.
   */
  private async readMetadataRecords(): Promise<PersistedEnrollmentMetadata[]> {
    const payload = await this.metadata.getItem(ENROLLMENT_COLLECTION_KEY);
    if (!payload) {
      return [];
    }
    try {
      const candidate = JSON.parse(payload) as unknown;
      if (!Array.isArray(candidate)) {
        return [];
      }
      return (candidate as PersistedEnrollmentMetadata[]).map(item =>
        hydrateInstallationMetadata(item),
      );
    } catch (error) {
      console.warn('[enrollmentStorage] Unreadable enrollment collection during write:', error);
      return [];
    }
  }

  private async persistRawMetadata(records: PersistedEnrollmentMetadata[]) {
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(records));
  }

  private async attachProofToken(
    record: PersistedEnrollmentMetadata,
  ): Promise<StoredEnrollment | undefined> {
    const secureKey = this.proofTokenStorageKey(record.id);
    const secureProofToken = await this.secure.getItem(secureKey);
    const legacyProofToken = record.enrollmentProofToken;
    const enrollmentProofToken = secureProofToken ?? legacyProofToken;

    if (!enrollmentProofToken) {
      if (__DEV__) {
        console.warn('[enrollmentStorage] Missing secure enrollment proof token:', record.id);
      }
      return undefined;
    }

    if (!secureProofToken && legacyProofToken) {
      await this.secure.setItem(secureKey, legacyProofToken);
    }

    return {
      ...record,
      enrollmentProofToken,
      approvalPolicy: normalizeEnrollmentApprovalPolicy(record.approvalPolicy),
    };
  }

  private async attachIntegrationPublicKey(
    record: StoredEnrollment,
  ): Promise<StoredEnrollment | undefined> {
    const secureKey = this.integrationPublicKeyStorageKey(record.id);
    const secureIntegrationPublicKey = await this.secure.getItem(secureKey);
    const legacyIntegrationPublicKey = record.integrationPublicKey;
    const integrationPublicKey = secureIntegrationPublicKey ?? legacyIntegrationPublicKey;

    if (!integrationPublicKey) {
      if (__DEV__) {
        console.warn('[enrollmentStorage] Missing secure integration public key:', record.id);
      }
      return undefined;
    }

    if (!secureIntegrationPublicKey && legacyIntegrationPublicKey) {
      await this.secure.setItem(secureKey, legacyIntegrationPublicKey);
    }

    return {
      ...record,
      integrationPublicKey,
      approvalPolicy: normalizeEnrollmentApprovalPolicy(record.approvalPolicy),
    };
  }

  private async persistMetadataRecords(records: StoredEnrollment[]) {
    const nextItems = records.map(item => this.stripSensitiveFields(hydrateInstallationMetadata(item)));
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
  }

  /**
   * Lists persisted enrollments with explicit failure discrimination (MOB-015).
   *
   * A corrupt collection payload or per-row secret failure is never collapsed into a healthy
   * empty list: unusable rows are returned as {@link BrokenEnrollment} descriptors and a corrupt
   * collection sets {@link EnrollmentListResult#collectionError}. The corrupt payload stays on
   * disk so Danger Zone clear-all remains the explicit recovery path.
   *
   * @return Healthy enrollments, broken descriptors, and the collection-level error flag.
   * @since 2026
   */
  async listEnrollmentsDetailed(): Promise<EnrollmentListResult> {
    const payload = await this.metadata.getItem(ENROLLMENT_COLLECTION_KEY);
    if (!payload) {
      return {enrollments: [], broken: [], collectionError: false};
    }

    let parsed: PersistedEnrollmentMetadata[];
    try {
      const candidate = JSON.parse(payload) as unknown;
      if (!Array.isArray(candidate)) {
        throw new Error('Enrollment collection payload is not an array');
      }
      parsed = candidate as PersistedEnrollmentMetadata[];
    } catch (error) {
      console.warn('[enrollmentStorage] Unreadable enrollment collection (kept on disk):', error);
      return {enrollments: [], broken: [], collectionError: true};
    }

    const hydratedItems = parsed.map(item => hydrateInstallationMetadata(item));
    const enrollments: StoredEnrollment[] = [];
    const broken: BrokenEnrollment[] = [];

    for (const item of hydratedItems) {
      try {
        const withProofToken = await this.attachProofToken(item);
        if (!withProofToken) {
          broken.push(this.toBrokenEnrollment(item, 'missing_proof_token'));
          continue;
        }
        const withIntegrationKey = await this.attachIntegrationPublicKey(withProofToken);
        if (!withIntegrationKey) {
          broken.push(this.toBrokenEnrollment(item, 'missing_integration_public_key'));
          continue;
        }
        enrollments.push(withIntegrationKey);
      } catch (error) {
        console.warn(
          '[enrollmentStorage] Secret rehydration failed (marking enrollment unusable):',
          item.id,
          error,
        );
        broken.push(this.toBrokenEnrollment(item, 'secret_rehydration_failed'));
      }
    }

    const healthyById = new Map(enrollments.map(item => [item.id, item]));
    const requiresMetadataRewrite = hydratedItems.some(
      item =>
        healthyById.has(item.id) &&
        (Object.hasOwn(item, 'enrollmentProofToken') ||
          Object.hasOwn(item, 'integrationPublicKey') ||
          Object.hasOwn(item, 'securityLevel') ||
          !Object.hasOwn(item, 'approvalPolicy')),
    );
    if (requiresMetadataRewrite) {
      // Legacy cleanup only rewrites rows that fully rehydrated; broken rows keep their
      // persisted metadata untouched so no recoverable material is destroyed.
      const nextRaw = hydratedItems.map(item => {
        const healthy = healthyById.get(item.id);
        return healthy
          ? this.stripSensitiveFields(hydrateInstallationMetadata(healthy))
          : item;
      });
      await this.persistRawMetadata(nextRaw);
    }

    return {enrollments, broken, collectionError: false};
  }

  /**
   * Lists all persisted enrollments that fully rehydrated (healthy rows only).
   *
   * Rehydrates the nested installation object even when older local records still use flattened
   * installation fields. Callers that must surface unusable rows use
   * {@link listEnrollmentsDetailed} instead.
   *
   * @return Array of stored enrollments.
   * @since 2025
   */
  async listEnrollments(): Promise<StoredEnrollment[]> {
    const result = await this.listEnrollmentsDetailed();
    return result.enrollments;
  }

  /**
   * Adds or updates an enrollment record.
   *
   * With EC P-256, device keys are stored through the native platform keystore path per enrollment,
   * so no device alias needs to be stored.
   *
   * @param record Enrollment payload to persist.
   * @since 2025
   */
  async saveEnrollment(record: StoredEnrollment) {
    await this.secure.setItem(this.proofTokenStorageKey(record.id), record.enrollmentProofToken);
    await this.secure.setItem(
      this.integrationPublicKeyStorageKey(record.id),
      record.integrationPublicKey ?? '',
    );
    // Raw metadata read so rows with currently unusable secrets survive the rewrite (MOB-015).
    const items = await this.readMetadataRecords();
    const nextRecord = this.stripSensitiveFields(
      hydrateInstallationMetadata({
        ...record,
        approvalPolicy: normalizeEnrollmentApprovalPolicy(
          record.approvalPolicy ?? DEFAULT_ENROLLMENT_APPROVAL_POLICY,
        ),
      }),
    );
    const nextItems = items.filter(item => item.id !== record.id).concat(nextRecord);
    await this.persistRawMetadata(nextItems);
  }

  /**
   * Replaces the full enrollment collection.
   *
   * @param records Enrollment records to persist.
   * @since 2025
   */
  async replaceAll(records: StoredEnrollment[]) {
    const nextItems = records.map(item =>
      hydrateInstallationMetadata({
        ...item,
        approvalPolicy: normalizeEnrollmentApprovalPolicy(
          item.approvalPolicy ?? DEFAULT_ENROLLMENT_APPROVAL_POLICY,
        ),
      }),
    );
    const currentItems = await this.readMetadataRecords();
    const nextIds = new Set(nextItems.map(item => item.id));

    await Promise.all(
      nextItems.map(item =>
        this.secure.setItem(this.proofTokenStorageKey(item.id), item.enrollmentProofToken),
      ),
    );
    await Promise.all(
      nextItems.map(item =>
        this.secure.setItem(
          this.integrationPublicKeyStorageKey(item.id),
          item.integrationPublicKey ?? '',
        ),
      ),
    );
    await Promise.all(
      currentItems
        .filter(item => !nextIds.has(item.id))
        .map(item => this.secure.removeItem(this.proofTokenStorageKey(item.id))),
    );
    await Promise.all(
      currentItems
        .filter(item => !nextIds.has(item.id))
        .map(item => this.secure.removeItem(this.integrationPublicKeyStorageKey(item.id))),
    );

    await this.persistMetadataRecords(nextItems);
  }

  /**
   * Retrieves an enrollment by identifier.
   *
   * @param id Enrollment identifier.
   * @return Enrollment or undefined when missing.
   * @since 2025
   */
  async getEnrollmentById(id: string): Promise<StoredEnrollment | undefined> {
    const items = await this.listEnrollments();
    return items.find(item => item.id === id);
  }

  /**
   * Removes enrollment metadata, sealed secrets, and the native keystore key pair.
   *
   * Native key deletion is best-effort (fail-open): storage cleanup always proceeds.
   *
   * @param id Enrollment identifier.
   * @since 2025
   */
  async deleteEnrollment(id: string) {
    await this.deleteKeyPairBestEffort(id);
    // Raw metadata read so this also removes rows whose secrets are unusable, and never
    // silently drops sibling broken rows from the collection (MOB-015).
    const items = await this.readMetadataRecords();
    const nextItems = items.filter(item => item.id !== id);
    await this.secure.removeItem(this.proofTokenStorageKey(id));
    await this.secure.removeItem(this.integrationPublicKeyStorageKey(id));
    await this.persistRawMetadata(nextItems);
  }

  /**
   * Updates the local last-activity timestamp for a stored enrollment.
   *
   * @param id Enrollment identifier.
   * @param lastActivityAt ISO timestamp to persist.
   * @return Updated enrollment when found.
   * @since 2025
   */
  async updateEnrollmentLastActivity(id: string, lastActivityAt: string) {
    const items = await this.readMetadataRecords();
    let updatedRaw: PersistedEnrollmentMetadata | undefined;
    const nextItems = items.map(item => {
      if (item.id !== id) {
        return item;
      }

      updatedRaw = {
        ...item,
        lastActivityAt,
      };
      return updatedRaw;
    });

    if (!updatedRaw) {
      return undefined;
    }

    await this.persistRawMetadata(nextItems);

    // Rehydrate only the updated row; rows whose secrets are unusable resolve to undefined,
    // which is fine because they cannot run pending checks in the first place (MOB-015).
    try {
      const withProofToken = await this.attachProofToken(updatedRaw);
      if (!withProofToken) {
        return undefined;
      }
      return await this.attachIntegrationPublicKey(withProofToken);
    } catch (error) {
      console.warn(
        '[enrollmentStorage] Secret rehydration failed after last-activity update:',
        id,
        error,
      );
      return undefined;
    }
  }

  /**
   * Applies refreshed installation metadata to persisted records by enrollment id.
   *
   * Operates on raw metadata so rows whose secrets are currently unusable keep their persisted
   * record instead of being dropped by a full healthy-list replace (MOB-015).
   *
   * @param updates Enrollment id / refreshed installation pairs.
   * @return True when at least one record was updated.
   * @since 2026
   */
  async updateInstallationMetadata(
    updates: Array<{id: string; installation: Installation}>,
  ): Promise<boolean> {
    if (updates.length === 0) {
      return false;
    }

    const records = await this.readMetadataRecords();
    const installationsById = new Map(updates.map(update => [update.id, update.installation]));
    let changed = false;
    const nextItems = records.map(record => {
      const installation = installationsById.get(record.id);
      if (!installation) {
        return record;
      }
      changed = true;
      return {...record, installation};
    });

    if (!changed) {
      return false;
    }

    await this.persistRawMetadata(nextItems);
    return true;
  }

  /**
   * Clears all enrollment data from storage and best-effort deletes native key material.
   *
   * True local reset per the MOB-015 locked UI contract: also sweeps orphaned enrollment secret
   * entries (recovers from a corrupt collection) and deletes the app-level seal key
   * `ezkey_app_seal_v1` so re-enrollment starts from a fresh seal key. All native deletions are
   * fail-open: storage cleanup always proceeds and failures stay observable in logs.
   *
   * @since 2025
   */
  async clearAll() {
    const items = await this.readMetadataRecords();
    await Promise.all(items.map(item => this.deleteKeyPairBestEffort(item.id)));
    await Promise.all(
      items.flatMap(item => [
        this.secure.removeItem(this.proofTokenStorageKey(item.id)),
        this.secure.removeItem(this.integrationPublicKeyStorageKey(item.id)),
      ]),
    );
    await this.metadata.removeItem(ENROLLMENT_COLLECTION_KEY);
    await this.sweepEnrollmentSecretRemnants();
    await this.deleteAppSealKeyBestEffort();
  }

  /**
   * Best-effort sweep of enrollment secret entries that id-based cleanup could not reach,
   * e.g. when the collection payload was corrupt and enrollment ids were unknown.
   */
  private async sweepEnrollmentSecretRemnants(): Promise<void> {
    try {
      const keys = await this.metadata.getAllKeys();
      const prefixes = [
        `${ENROLLMENT_PROOF_TOKEN_KEY_PREFIX}.`,
        `${INTEGRATION_PUBLIC_KEY_KEY_PREFIX}.`,
        `${SEALED_SECRET_KEY_PREFIX}.${ENROLLMENT_PROOF_TOKEN_KEY_PREFIX}.`,
        `${SEALED_SECRET_KEY_PREFIX}.${INTEGRATION_PUBLIC_KEY_KEY_PREFIX}.`,
      ];
      const remnants = keys.filter(key => prefixes.some(prefix => key.startsWith(prefix)));
      if (remnants.length > 0) {
        await this.metadata.removeMany(remnants);
      }
    } catch (error) {
      console.warn(
        '[enrollmentStorage] Failed to sweep enrollment secret remnants (continuing wipe):',
        error,
      );
    }
  }

  /**
   * Best-effort deletion of the shared app seal key on clear-all (fail-open, observable).
   */
  private async deleteAppSealKeyBestEffort(): Promise<void> {
    try {
      await nativeCrypto.deleteAppSealKey();
    } catch (error) {
      console.warn(
        '[enrollmentStorage] Failed to delete app seal key (continuing wipe):',
        error,
      );
    }
  }
}

export const enrollmentStorage = EnrollmentStorage.create();

export type EnrollmentStorageInstance = EnrollmentStorage;
