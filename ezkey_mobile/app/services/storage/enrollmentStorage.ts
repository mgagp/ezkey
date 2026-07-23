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
import {EnrollmentSummary} from '../api/types';
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

type PersistedEnrollmentMetadata = Omit<StoredEnrollment, 'enrollmentProofToken'> & {
  enrollmentProofToken?: string;
  integrationPublicKey?: string;
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

  private stripSensitiveFields(record: StoredEnrollment): PersistedEnrollmentMetadata {
    const {
      enrollmentProofToken: _enrollmentProofToken,
      integrationPublicKey: _integrationPublicKey,
      securityLevel: _legacySecurityLevel,
      ...metadata
    } = record;
    return metadata;
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
   * Lists all persisted enrollments.
   *
    * Rehydrates the nested installation object even when older local records still use flattened
    * installation fields.
    *
    * @return Array of stored enrollments.
   * @since 2025
   */
  async listEnrollments(): Promise<StoredEnrollment[]> {
    const payload = await this.metadata.getItem(ENROLLMENT_COLLECTION_KEY);
    if (!payload) {
      return [];
    }
    try {
      const parsed = JSON.parse(payload) as PersistedEnrollmentMetadata[];
      const hydratedItems = parsed.map(item => hydrateInstallationMetadata(item));
      const withProofTokens = await Promise.all(
        hydratedItems.map(item => this.attachProofToken(item)),
      );
      const withIntegrationKeys = await Promise.all(
        withProofTokens
          .filter((item): item is StoredEnrollment => item != null)
          .map(item => this.attachIntegrationPublicKey(item)),
      );
      const nextItems = withIntegrationKeys.filter((item): item is StoredEnrollment => item != null);
      const requiresMetadataRewrite = hydratedItems.some(
        item =>
          Object.hasOwn(item, 'enrollmentProofToken') ||
          Object.hasOwn(item, 'integrationPublicKey') ||
          Object.hasOwn(item, 'securityLevel') ||
          !Object.hasOwn(item, 'approvalPolicy'),
      );
      if (requiresMetadataRewrite) {
        await this.persistMetadataRecords(nextItems);
      }
      return nextItems;
    } catch (error) {
      console.warn('[enrollmentStorage] Failed to parse enrollment cache:', error);
      return [];
    }
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
    const items = await this.listEnrollments();
    const nextItems = items
      .filter(item => item.id !== record.id)
      .concat(
        hydrateInstallationMetadata({
          ...record,
          approvalPolicy: normalizeEnrollmentApprovalPolicy(
            record.approvalPolicy ?? DEFAULT_ENROLLMENT_APPROVAL_POLICY,
          ),
        }),
      );
    await this.persistMetadataRecords(nextItems);
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
    const currentItems = await this.listEnrollments();
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
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== id);
    await this.secure.removeItem(this.proofTokenStorageKey(id));
    await this.secure.removeItem(this.integrationPublicKeyStorageKey(id));
    await this.persistMetadataRecords(nextItems);
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
    const items = await this.listEnrollments();
    let updatedRecord: StoredEnrollment | undefined;
    const nextItems = items.map(item => {
      if (item.id !== id) {
        return item;
      }

      updatedRecord = {
        ...item,
        lastActivityAt,
      };
      return updatedRecord;
    });

    if (!updatedRecord) {
      return undefined;
    }

    await this.persistMetadataRecords(nextItems);
    return updatedRecord;
  }

  /**
   * Clears all enrollment data from storage and best-effort deletes native key pairs.
   *
   * Useful for development/testing or complete reset scenarios. Native key deletion is
   * fail-open: sealed-secret and metadata cleanup always proceeds.
   *
   * @since 2025
   */
  async clearAll() {
    const items = await this.listEnrollments();
    await Promise.all(items.map(item => this.deleteKeyPairBestEffort(item.id)));
    await Promise.all(
      items.flatMap(item => [
        this.secure.removeItem(this.proofTokenStorageKey(item.id)),
        this.secure.removeItem(this.integrationPublicKeyStorageKey(item.id)),
      ]),
    );
    await this.metadata.removeItem(ENROLLMENT_COLLECTION_KEY);
  }
}

export const enrollmentStorage = EnrollmentStorage.create();

export type EnrollmentStorageInstance = EnrollmentStorage;
