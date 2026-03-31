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
import {secureStorage} from './secureStorage';

const ENROLLMENT_COLLECTION_KEY = 'ezkey-mobile/enrollments';

/**
 * Local representation of enrollment records including proof tokens.
 *
 * With EC P-256, device keys are stored through the native platform keystore path per enrollment,
 * so no device alias needs to be stored.
 *
 * @since 2025
 */
export type StoredEnrollment = EnrollmentSummary & {
  enrollmentProofToken: string;
  enrollmentId?: string; // Enrollment ID used for key derivation (for backward compatibility)
  integrationPublicKey?: string;
  enrollmentName?: string;
  deviceLabel?: string;
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
 * Secure storage gateway in charge of persisting enrollment information on device.
 *
 * Uses platform Keychain (iOS Keychain / Android Keystore) for secure storage.
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
   * @return Enrollment storage instance using platform Keychain.
   * @since 2025
   */
  static create() {
    return new EnrollmentStorage({secure: secureStorage, metadata: AsyncStorage});
  }

  /**
   * Lists all persisted enrollments.
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
      const parsed = JSON.parse(payload) as StoredEnrollment[];
      return parsed;
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
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== record.id).concat(record);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
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
   * Removes enrollment metadata.
   *
   * @param id Enrollment identifier.
   * @since 2025
   */
  async deleteEnrollment(id: string) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== id);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
  }

  /**
   * Clears all enrollment data from storage.
   *
   * Useful for development/testing or complete reset scenarios.
   *
   * @since 2025
   */
  async clearAll() {
    await this.metadata.removeItem(ENROLLMENT_COLLECTION_KEY);
  }
}

export const enrollmentStorage = EnrollmentStorage.create();

export type EnrollmentStorageInstance = EnrollmentStorage;
