/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentScannerModal
 * Description: React Native modal that scans enrollment QR codes via VisionCamera 5 barcode output (ML Kit).
 * Security Context: Aligns with docs/CRYPTO.md and docs/features/AUTH_SECURITY.md to preserve anti-enumeration guarantees
 *                   by limiting QR payload reuse and adhering to read-once token semantics.
 * @since 2025
 */

import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Modal, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {Camera} from 'react-native-vision-camera';
import {
  useBarcodeScannerOutput,
  type Barcode,
} from 'react-native-vision-camera-barcode-scanner';

/** Formats aligned with legacy ML Kit enrollment scanner (QR-first). */
const ENROLLMENT_BARCODE_FORMATS = [
  'qr-code',
  'aztec',
  'data-matrix',
  'pdf-417',
  'code-128',
] as const;

/**
 * Modal properties for the enrollment QR scanner.
 *
 * @since 2025
 */
type Props = {
  visible: boolean;
  onDismiss: () => void;
  onScanned: (value: string) => void;
};

/**
 * Displays the secure enrollment scanner overlay responsible for capturing QR codes emitted by the Admin API.
 *
 * Uses {@link useBarcodeScannerOutput} (ML Kit on iOS and Android).
 *
 * @param visible Whether the modal should be displayed.
 * @param onDismiss Callback invoked when the user cancels the modal.
 * @param onScanned Callback invoked with the decoded QR payload when a new value is detected.
 * @since 2025
 */
export const EnrollmentScannerModal: React.FC<Props> = ({visible, onDismiss, onScanned}) => {
  const [isActive, setIsActive] = useState(false);
  const lastScannedRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    setIsActive(visible);
    if (!visible) {
      lastScannedRef.current = undefined;
    }
  }, [visible]);

  const handleScanned = useCallback(
    (value: string) => {
      if (lastScannedRef.current === value) {
        return;
      }
      lastScannedRef.current = value;
      setIsActive(false);
      onScanned(value);
    },
    [onScanned],
  );

  const onBarcodeScanned = useCallback(
    (barcodes: Barcode[]) => {
      for (const barcode of barcodes) {
        const value = barcode.rawValue;
        if (value == null || value.length === 0) {
          continue;
        }
        handleScanned(value);
        return;
      }
    },
    [handleScanned],
  );

  const onScannerError = useCallback((_error: Error) => {
    // Scanner errors are rare; enrollment wizard surfaces camera permission issues separately.
  }, []);

  const barcodeOutput = useBarcodeScannerOutput({
    barcodeFormats: [...ENROLLMENT_BARCODE_FORMATS],
    onBarcodeScanned,
    onError: onScannerError,
  });

  const content = useMemo(
    () => (
      <Camera
        style={StyleSheet.absoluteFill}
        device="back"
        isActive={isActive}
        outputs={[barcodeOutput]}
      />
    ),
    [barcodeOutput, isActive],
  );

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onDismiss} transparent>
      <View style={styles.backdrop}>
        <View style={styles.sheet}>
          <Text style={styles.title}>Scan enrollment QR</Text>
          <View style={styles.cameraContainer}>{content}</View>
          <View style={styles.actions}>
            <TouchableOpacity onPress={onDismiss} style={styles.buttonSecondary}>
              <Text style={styles.buttonSecondaryLabel}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    padding: 16,
  },
  sheet: {
    backgroundColor: '#0b0d11',
    borderRadius: 16,
    overflow: 'hidden',
    padding: 16,
    gap: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
    textAlign: 'center',
  },
  cameraContainer: {
    width: '100%',
    aspectRatio: 3 / 4,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: '#000',
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 12,
  },
  buttonSecondary: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#f4f7ff',
  },
  buttonSecondaryLabel: {
    color: '#f4f7ff',
    fontSize: 14,
    fontWeight: '500',
  },
});
