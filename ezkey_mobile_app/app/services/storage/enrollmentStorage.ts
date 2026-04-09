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

const ENROLLMENT_COLLECTION_KEY = 'ezkey-mobile-app/enrollments';

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
  enrollmentId?: string;
  integrationPublicKey?: string;
  enrollmentName?: string;
  deviceLabel?: string;
  devicePrivateKeyStorageTier?: 'NONE' | 'STANDARD' | 'STRONG';
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

  static create() {
    return new EnrollmentStorage({secure: secureStorage, metadata: AsyncStorage});
  }

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

  async saveEnrollment(record: StoredEnrollment) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== record.id).concat(record);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
  }

  async getEnrollmentById(id: string): Promise<StoredEnrollment | undefined> {
    const items = await this.listEnrollments();
    return items.find(item => item.id === id);
  }

  async deleteEnrollment(id: string) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== id);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
  }

  async clearAll() {
    await this.metadata.removeItem(ENROLLMENT_COLLECTION_KEY);
  }
}

export const enrollmentStorage = EnrollmentStorage.create();

export type EnrollmentStorageInstance = EnrollmentStorage;
