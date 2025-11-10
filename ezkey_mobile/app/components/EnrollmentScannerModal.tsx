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

type Props = {
  visible: boolean;
  onDismiss: () => void;
  onScanned: (value: string) => void;
};

const scanEzkeyPlugin: FrameProcessorPlugin | undefined =
  VisionCameraProxy.initFrameProcessorPlugin('scanEzkey', {}) ?? undefined;

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

