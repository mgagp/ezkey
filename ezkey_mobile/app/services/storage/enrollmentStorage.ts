/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentStorage
 * Description: Persistence facade that splits sensitive enrollment metadata between secure storage and AsyncStorage.
 * Security Context: Implements the storage strategy described in docs/features/AUTH_SECURITY.md by isolating proof tokens
 *                   from user-friendly metadata and supporting device alias retrieval without exposing cryptographic material.
 * @since 2025
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import {EnrollmentSummary} from '../api/types';
import {mockSecureStorage} from './mockSecureStorage';
import {secureStorage} from './secureStorage';

const ENROLLMENT_COLLECTION_KEY = 'ezkey-mobile/enrollments';

/**
 * Local representation of enrollment records including proof tokens and device alias metadata.
 *
 * @since 2025
 */
export type StoredEnrollment = EnrollmentSummary & {
  enrollmentProofToken: string;
  deviceAlias: string;
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
   * Factory used to instantiate the storage module, optionally using a mock secure store for development.
   *
   * @param useMockSecure Whether to rely on the mock secure storage adapter.
   * @return Enrollment storage instance.
   * @since 2025
   */
  static create({useMockSecure}: {useMockSecure?: boolean} = {}) {
    const secure = useMockSecure ? mockSecureStorage : secureStorage;
    return new EnrollmentStorage({secure, metadata: AsyncStorage});
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
   * Adds or updates an enrollment record and stores the device alias in secure storage.
   *
   * @param record Enrollment payload to persist.
   * @since 2025
   */
  async saveEnrollment(record: StoredEnrollment) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== record.id).concat(record);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
    await this.secure.setItem(this.aliasKey(record.id), record.deviceAlias);
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
   * Removes enrollment metadata and its secure alias entry.
   *
   * @param id Enrollment identifier.
   * @since 2025
   */
  async deleteEnrollment(id: string) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== id);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
    await this.secure.removeItem(this.aliasKey(id));
  }

  /**
   * Retrieves the device alias bound to a specific enrollment from secure storage.
   *
   * @param id Enrollment identifier.
   * @return Device alias or undefined.
   * @since 2025
   */
  async getDeviceAlias(id: string): Promise<string | undefined> {
    return this.secure.getItem(this.aliasKey(id));
  }

  private aliasKey(id: string) {
    return `enrollment-alias/${id}`;
  }
}

const DEFAULT_USE_MOCK = __DEV__;

export const enrollmentStorage = EnrollmentStorage.create({useMockSecure: DEFAULT_USE_MOCK});

export type EnrollmentStorageInstance = EnrollmentStorage;

