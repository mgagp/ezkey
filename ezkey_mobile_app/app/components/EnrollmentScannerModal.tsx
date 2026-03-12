/*
 * Enrollment QR scanner using Vision Camera and scanEzkey frame processor.
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
import {colors, spacing, typography, borderRadius} from '../config/theme';

type Props = {
  visible: boolean;
  onDismiss: () => void;
  onScanned: (value: string) => void;
};

const scanEzkeyPlugin: FrameProcessorPlugin | undefined =
  VisionCameraProxy.initFrameProcessorPlugin('scanEzkey', {}) ?? undefined;

export const EnrollmentScannerModal: React.FC<Props> = ({
  visible,
  onDismiss,
  onScanned,
}) => {
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
      if (lastScannedRef.current === value) return;
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
      if (frameCounter.value % 2 !== 0) return;
      if (scanEzkeyPlugin == null) return;
      const data = scanEzkeyPlugin.call(frame) as string[] | undefined;
      if (!data || data.length === 0) return;
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
    <Modal
      visible={visible}
      animationType="slide"
      onRequestClose={onDismiss}
      transparent>
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
    backgroundColor: colors.overlay,
    justifyContent: 'center',
    padding: spacing.lg,
  },
  sheet: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.xl,
    overflow: 'hidden',
    padding: spacing.lg,
    gap: spacing.lg,
  },
  title: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  cameraContainer: {
    width: '100%',
    aspectRatio: 3 / 4,
    borderRadius: borderRadius.lg,
    overflow: 'hidden',
    backgroundColor: colors.background,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: spacing.md,
  },
  buttonSecondary: {
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.borderStrong,
  },
  buttonSecondaryLabel: {
    color: colors.textPrimary,
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  message: {
    color: colors.textPrimary,
    fontSize: typography.fontSize.base,
  },
});
