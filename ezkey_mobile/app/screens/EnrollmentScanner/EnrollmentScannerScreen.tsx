import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {
  Camera,
  VisionCameraProxy,
  useCameraDevice,
  useCameraPermission,
  useFrameProcessor,
} from 'react-native-vision-camera';
import {useRunOnJS, useSharedValue} from 'react-native-worklets-core';
import {RootStackParamList} from '../../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentScanner'>;

type ParsedQrPayload = {
  enrollmentId: string;
  enrollmentProofToken: string;
  language?: string;
};

export const EnrollmentScannerScreen: React.FC<Props> = ({navigation}) => {
  const {hasPermission, requestPermission} = useCameraPermission();
  const device = useCameraDevice('back');
  const [isActive, setIsActive] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [torchEnabled, setTorchEnabled] = useState(false);
  const lastScannedValueRef = useRef<string | undefined>();
  const isActiveRef = useRef(isActive);
  const frameCounter = useSharedValue(0);

  useFocusEffect(
    useCallback(() => {
      setIsActive(true);
      return () => setIsActive(false);
    }, []),
  );

  const handlePermission = useCallback(async () => {
    const granted = await requestPermission();
    if (!granted) {
      Alert.alert(
        'Camera permission required',
        'Enable camera access in settings to scan Ezkey enrollment QR codes.',
      );
    }
  }, [requestPermission]);

  useEffect(() => {
    isActiveRef.current = isActive;
  }, [isActive]);

  const onQrScanned = useCallback(
    (data: string) => {
      try {
        const parsed = parseQrPayload(data);
        setIsActive(false);
        navigation.replace('EnrollmentWizard', {scanned: parsed});
      } catch (parseError) {
        console.warn('[EnrollmentScanner] Invalid QR payload:', parseError);
        setError('Invalid QR code. Ensure you scan an Ezkey enrollment QR.');
      }
    },
    [navigation],
  );

  const handleDetection = useCallback(
    (codes: string[]) => {
      if (!codes.length) {
        return;
      }
      const value = codes[0];
      if (!isActiveRef.current) {
        return;
      }
      if (lastScannedValueRef.current === value) {
        return;
      }
      lastScannedValueRef.current = value;
      onQrScanned(value);
    },
    [onQrScanned],
  );

  const runOnJsDetection = useRunOnJS(handleDetection, [handleDetection]);

  const frameProcessor = useFrameProcessor(frame => {
    'worklet';
    frameCounter.value = frameCounter.value + 1;
    if (frameCounter.value % 3 !== 0) {
      return;
    }
    const plugin = VisionCameraProxy.initFrameProcessorPlugin('scanEzkey', {}) ?? undefined;
    if (plugin == null) {
      return;
    }
    const result = plugin.call(frame) as string[] | undefined;
    if (!result || result.length === 0) {
      return;
    }
    runOnJsDetection(result);
  }, [runOnJsDetection]);

  const permissionContent = useMemo(() => {
    if (hasPermission === true) {
      return null;
    }
    if (hasPermission === false) {
      return (
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionTitle}>Camera access required</Text>
          <Text style={styles.permissionBody}>
            Enable camera permissions to scan Ezkey enrollment QR codes. You can grant access now or
            update it later in the system settings.
          </Text>
          <TouchableOpacity style={styles.permissionButton} onPress={handlePermission}>
            <Text style={styles.permissionButtonLabel}>Grant permission</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return (
      <View style={styles.permissionContainer}>
        <Text style={styles.permissionTitle}>Requesting permission…</Text>
        <TouchableOpacity style={styles.permissionButton} onPress={handlePermission}>
          <Text style={styles.permissionButtonLabel}>Grant permission</Text>
        </TouchableOpacity>
      </View>
    );
  }, [handlePermission, hasPermission]);

  return (
    <View style={styles.container}>
      {hasPermission && isActive && device ? (
        <Camera
          style={StyleSheet.absoluteFill}
          isActive={isActive}
          device={device}
          frameProcessor={frameProcessor}
          torch={torchEnabled ? 'on' : 'off'}
        />
      ) : null}
      <View style={styles.overlay}>
        <Text style={styles.instructions}>Align the Ezkey QR code within the frame</Text>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <View style={styles.actions}>
          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={() => {
              setIsActive(false);
              navigation.goBack();
            }}>
            <Text style={styles.secondaryLabel}>Cancel</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={() => setTorchEnabled(current => !current)}>
            <Text style={styles.secondaryLabel}>{torchEnabled ? 'Torch off' : 'Torch on'}</Text>
          </TouchableOpacity>
        </View>
      </View>
      {permissionContent}
    </View>
  );
};

const parseQrPayload = (value: string): ParsedQrPayload => {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error('Empty payload');
  }
  try {
    const json = JSON.parse(trimmed);
    if (json.enrollmentId && json.enrollmentProofToken) {
      return {
        enrollmentId: String(json.enrollmentId),
        enrollmentProofToken: String(json.enrollmentProofToken),
        language: json.language ? String(json.language) : undefined,
      };
    }
  } catch (error) {
    // not JSON, fallback to pipe format
  }
  const pipeParts = trimmed.split('|');
  if (pipeParts.length >= 2) {
    return {
      enrollmentId: pipeParts[0],
      enrollmentProofToken: pipeParts.slice(1).join('|'),
    };
  }
  throw new Error('Unsupported QR format');
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  overlay: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 24,
    gap: 12,
    backgroundColor: 'rgba(11, 13, 17, 0.8)',
  },
  instructions: {
    fontSize: 16,
    color: '#f4f7ff',
    textAlign: 'center',
  },
  error: {
    fontSize: 14,
    color: '#ff6666',
    textAlign: 'center',
  },
  actions: {
    flexDirection: 'row',
    gap: 16,
    justifyContent: 'center',
  },
  secondaryButton: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 10,
    backgroundColor: '#151923',
  },
  secondaryLabel: {
    fontSize: 14,
    color: '#f4f7ff',
  },
  permissionContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#0b0d11',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 12,
  },
  permissionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
    textAlign: 'center',
  },
  permissionBody: {
    fontSize: 14,
    color: '#c2c8d5',
    textAlign: 'center',
  },
  permissionButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
    backgroundColor: '#61d095',
  },
  permissionButtonLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#0b0d11',
  },
});

