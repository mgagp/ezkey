/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentCodeOutput (Android)
 * Description: Enrollment code output backed by VisionCamera barcode scanner (Google ML Kit).
 * @since 2026
 */

import {useCallback} from 'react';
import {
  useBarcodeScannerOutput,
  type Barcode,
} from 'react-native-vision-camera-barcode-scanner';
import type {UseEnrollmentCodeOutput} from './enrollmentCodeOutput.types';

/** Formats aligned with legacy ML Kit enrollment scanner (QR-first). */
const ENROLLMENT_BARCODE_FORMATS = [
  'qr-code',
  'aztec',
  'data-matrix',
  'pdf-417',
  'code-128',
] as const;

export const useEnrollmentCodeOutput: UseEnrollmentCodeOutput = onCodeValue => {
  const onBarcodeScanned = useCallback(
    (barcodes: Barcode[]) => {
      for (const barcode of barcodes) {
        const value = barcode.rawValue;
        if (value == null || value.length === 0) {
          continue;
        }
        onCodeValue(value);
        return;
      }
    },
    [onCodeValue],
  );

  const onScannerError = useCallback((_error: Error) => {
    // Scanner errors are rare; enrollment wizard surfaces camera permission issues separately.
  }, []);

  return useBarcodeScannerOutput({
    barcodeFormats: [...ENROLLMENT_BARCODE_FORMATS],
    onBarcodeScanned,
    onError: onScannerError,
  });
};
