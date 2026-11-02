/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentCodeOutput (iOS)
 * Description: Enrollment code output backed by VisionCamera's native object output
 *              (AVFoundation AVCaptureMetadataOutput). No Google ML Kit on iOS: ML Kit ships
 *              no arm64 simulator slice and Apple's native detector is the platform norm.
 * @since 2026
 */

import {useCallback} from 'react';
import {
  isScannedCode,
  useObjectOutput,
  type ScannedObject,
} from 'react-native-vision-camera';
import type {UseEnrollmentCodeOutput} from './enrollmentCodeOutput.types';

/** Same QR-first format set as the Android ML Kit scanner. */
const ENROLLMENT_OBJECT_TYPES = ['qr', 'aztec', 'data-matrix', 'pdf-417', 'code-128'] as const;

export const useEnrollmentCodeOutput: UseEnrollmentCodeOutput = onCodeValue => {
  const onObjectsScanned = useCallback(
    (objects: ScannedObject[]) => {
      for (const object of objects) {
        if (!isScannedCode(object)) {
          continue;
        }
        const value = object.value;
        if (value == null || value.length === 0) {
          continue;
        }
        onCodeValue(value);
        return;
      }
    },
    [onCodeValue],
  );

  return useObjectOutput({
    types: [...ENROLLMENT_OBJECT_TYPES],
    onObjectsScanned,
  });
};
