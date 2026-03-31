/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useSecureEnrollmentInfo Hook
 * Description: Hook for checking enrollment availability.
 * Security Context: With Ed25519, device keys are derived on-demand from the root key,
 *                   so no device alias needs to be stored or retrieved.
 * @since 2025
 */

import {useQuery} from '@tanstack/react-query';
import {enrollmentStorage} from '../services/storage/enrollmentStorage';

type SecureEnrollmentInfo = {
  enrollmentId: string;
};

/**
 * Checks if enrollment exists and returns basic info.
 *
 * With Ed25519, keys are derived on-demand, so this hook mainly verifies enrollment exists.
 *
 * @param enrollmentId Enrollment identifier.
 * @return React Query result containing enrollment info if available.
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
      const enrollment = await enrollmentStorage.getEnrollmentById(enrollmentId);
      return enrollment ? {enrollmentId} : null;
    },
  });

