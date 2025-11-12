/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentScannerModal
 * Description: React Native modal that interfaces with the QR frame processor to bootstrap secure enrollment.
 * Security Context: Aligns with docs/CRYPTO.md and docs/features/AUTH_SECURITY.md to preserve anti-enumeration guarantees
 *                   by limiting QR payload reuse and adhering to read-once token semantics.
 * @since 2025
 */

import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Modal, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {
  Camera,
  VisionCameraProxy,
  useCameraDevice,
  useFrameProcessor,
} from 'react-native-vision-camera';
import type {FrameProcessorPlugin} from 'react-native-vision-camera';
import {useRunOnJS, useSharedValue} from 'react-native-worklets-core';

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
 * Native Vision Camera plugin hook used to bridge the Kotlin frame processor (`EzkeyQrFrameProcessorPlugin`)
 * with the JavaScript runtime. The plugin decodes QR payloads that encode enrollment proof tokens and other
 * bootstrap material documented in `docs/ENDPOINT.md`.
 *
 * The plugin name mirrors the Android implementation to keep the bridge deterministic across platforms.
 *
 * @since 2025
 */
const scanEzkeyPlugin: FrameProcessorPlugin | undefined =
  VisionCameraProxy.initFrameProcessorPlugin('scanEzkey', {}) ?? undefined;

/**
 * Displays the secure enrollment scanner overlay responsible for capturing QR codes emitted by the Admin API.
 *
 * The component throttles frame processing to minimize device workload while preserving the guarantees described in
 * `docs/features/AUTH_SECURITY.md`:
 * - Frames are sampled deterministically to avoid duplicate reads that could leak proof tokens.
 * - The last scanned payload is memoized to uphold the read-once semantics of `authAttemptProofToken`.
 * - The scan result is surfaced synchronously so the caller can persist the enrollment context before any reuse.
 *
 * @param visible Whether the modal should be displayed.
 * @param onDismiss Callback invoked when the user cancels the modal.
 * @param onScanned Callback invoked with the decoded QR payload when a new value is detected.
 * @since 2025
 */
export const EnrollmentScannerModal: React.FC<Props> = ({visible, onDismiss, onScanned}) => {
  const device = useCameraDevice('back');
  const [isActive, setIsActive] = useState(false);
  const lastScannedRef = useRef<string | undefined>();
  const frameCounter = useSharedValue(0);

  useEffect(() => {
    setIsActive(visible);
    if (!visible) {
      lastScannedRef.current = undefined;
      frameCounter.value = 0;
    }
  }, [frameCounter, visible]);

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

  const runOnJsDetection = useRunOnJS(handleScanned, [handleScanned]);

  const frameProcessor = useFrameProcessor(
    frame => {
      'worklet';
      frameCounter.value = frameCounter.value + 1;
      if (frameCounter.value % 2 !== 0) {
        return;
      }
      if (scanEzkeyPlugin == null) {
        return;
      }
      const data = scanEzkeyPlugin.call(frame) as string[] | undefined;
      if (!data || data.length === 0) {
        return;
      }
      runOnJsDetection(data[0]);
    },
    [frameCounter, runOnJsDetection],
  );

  const content = useMemo(() => {
    if (!device) {
      return (
        <View style={styles.centered}>
          <Text style={styles.message}>No camera device found.</Text>
        </View>
      );
    }
    return (
      <Camera
        style={StyleSheet.absoluteFill}
        device={device}
        isActive={isActive}
        frameProcessor={frameProcessor}
      />
    );
  }, [device, frameProcessor, isActive]);

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
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  message: {
    color: '#f4f7ff',
    fontSize: 14,
  },
});

