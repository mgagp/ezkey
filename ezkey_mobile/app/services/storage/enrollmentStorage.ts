import AsyncStorage from '@react-native-async-storage/async-storage';
import {EnrollmentSummary} from '../api/types';
import {mockSecureStorage} from './mockSecureStorage';
import {secureStorage} from './secureStorage';

const ENROLLMENT_COLLECTION_KEY = 'ezkey-mobile/enrollments';

type EnrollmentRecord = EnrollmentSummary & {
  enrollmentProofToken: string;
  deviceAlias: string;
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

class EnrollmentStorage {
  private secure: StorageDelegate;
  private metadata: typeof AsyncStorage;

  constructor(options: EnrollmentStorageOptions) {
    this.secure = options.secure;
    this.metadata = options.metadata;
  }

  static create(useMock = false) {
    const secure = useMock ? mockSecureStorage : secureStorage;
    return new EnrollmentStorage({secure, metadata: AsyncStorage});
  }

  async listEnrollments(): Promise<EnrollmentRecord[]> {
    const payload = await this.metadata.getItem(ENROLLMENT_COLLECTION_KEY);
    if (!payload) {
      return [];
    }
    try {
      const parsed = JSON.parse(payload) as EnrollmentRecord[];
      return parsed;
    } catch (error) {
      console.warn('[enrollmentStorage] Failed to parse enrollment cache:', error);
      return [];
    }
  }

  async saveEnrollment(record: EnrollmentRecord) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== record.id).concat(record);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
    await this.secure.setItem(this.aliasKey(record.id), record.deviceAlias);
  }

  async getEnrollmentById(id: string): Promise<EnrollmentRecord | undefined> {
    const items = await this.listEnrollments();
    return items.find(item => item.id === id);
  }

  async deleteEnrollment(id: string) {
    const items = await this.listEnrollments();
    const nextItems = items.filter(item => item.id !== id);
    await this.metadata.setItem(ENROLLMENT_COLLECTION_KEY, JSON.stringify(nextItems));
    await this.secure.removeItem(this.aliasKey(id));
  }

  async getDeviceAlias(id: string): Promise<string | undefined> {
    return this.secure.getItem(this.aliasKey(id));
  }

  private aliasKey(id: string) {
    return `enrollment-alias/${id}`;
  }
}

export const enrollmentStorage = EnrollmentStorage.create();

export type EnrollmentStorageInstance = EnrollmentStorage;

