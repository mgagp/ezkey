/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useSecureEnrollmentInfo Hook
 * Description: Lightweight selector for sensitive enrollment metadata cached locally with secure storage.
 * Security Context: Supports principle of least privilege described in docs/CRYPTO.md by exposing only non-cryptographic
 *                   metadata (device alias) to the UI layer while proof tokens remain sealed.
 * @since 2025
 */

import {useQuery} from '@tanstack/react-query';
import {enrollmentStorage} from '../services/storage/enrollmentStorage';

type SecureEnrollmentInfo = {
  deviceAlias: string;
};

/**
 * Fetches secure enrollment metadata limited to the device alias while ensuring proof tokens remain undisclosed.
 *
 * @param enrollmentId Enrollment identifier used to locate secure storage entries.
 * @return React Query result containing optional secure metadata.
 * @since 2025
 */
export const useSecureEnrollmentInfo = (enrollmentId: string) =>
  useQuery<SecureEnrollmentInfo | null>({
    queryKey: ['secure-enrollment', enrollmentId],
    enabled: Boolean(enrollmentId),
    queryFn: async () => {
      if (!enrollmentId) {
        return null;
      }
      const deviceAlias = await enrollmentStorage.getDeviceAlias(enrollmentId);
      return deviceAlias ? {deviceAlias} : null;
    },
  });

