/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: StorageService
 * Description: Secure storage for enrollment data
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

export interface EnrollmentData {
  enrollmentId: number;
  enrollmentName: string;
  enrollmentProofToken: string;
  integrationName: string;
  integrationDescription: string;
  integrationLogo?: string;
  integrationPublicKey: string;
  devicePublicKey: string;
  devicePrivateKey: string;
  verified: boolean;
  createdAt: string;
}

const STORAGE_KEY_PREFIX = '@ezkey_enrollment_';

class StorageService {
  async saveEnrollment(enrollment: EnrollmentData): Promise<void> {
    try {
      const key = `${STORAGE_KEY_PREFIX}${enrollment.enrollmentId}`;
      const value = JSON.stringify(enrollment);
      await AsyncStorage.setItem(key, value);
    } catch (error) {
      console.error('Failed to save enrollment:', error);
      throw new Error('Failed to save enrollment data');
    }
  }

  async getEnrollment(enrollmentId: number): Promise<EnrollmentData | null> {
    try {
      const key = `${STORAGE_KEY_PREFIX}${enrollmentId}`;
      const value = await AsyncStorage.getItem(key);
      if (value) {
        return JSON.parse(value);
      }
      return null;
    } catch (error) {
      console.error('Failed to get enrollment:', error);
      return null;
    }
  }

  async getAllEnrollments(): Promise<EnrollmentData[]> {
    try {
      const keys = await AsyncStorage.getAllKeys();
      const enrollmentKeys = keys.filter(key => key.startsWith(STORAGE_KEY_PREFIX));
      const enrollments: EnrollmentData[] = [];
      
      for (const key of enrollmentKeys) {
        const value = await AsyncStorage.getItem(key);
        if (value) {
          enrollments.push(JSON.parse(value));
        }
      }
      
      return enrollments.sort((a, b) => 
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
    } catch (error) {
      console.error('Failed to get all enrollments:', error);
      return [];
    }
  }

  async deleteEnrollment(enrollmentId: number): Promise<void> {
    try {
      const key = `${STORAGE_KEY_PREFIX}${enrollmentId}`;
      await AsyncStorage.removeItem(key);
    } catch (error) {
      console.error('Failed to delete enrollment:', error);
      throw new Error('Failed to delete enrollment');
    }
  }

  async clearAll(): Promise<void> {
    try {
      const keys = await AsyncStorage.getAllKeys();
      const enrollmentKeys = keys.filter(key => key.startsWith(STORAGE_KEY_PREFIX));
      await AsyncStorage.multiRemove(enrollmentKeys);
    } catch (error) {
      console.error('Failed to clear enrollments:', error);
      throw new Error('Failed to clear enrollments');
    }
  }
}

export default new StorageService();
