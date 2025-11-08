import React, {useCallback, useMemo, useState} from 'react';
import {Alert, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentWizard'>;

type WizardStep = {
  id: string;
  title: string;
  description: string;
  actionLabel: string;
  secondaryLabel?: string;
};

const MOCK_DEVICE_NAME = 'Pixel 7 Pro';

export const EnrollmentWizardScreen: React.FC<Props> = ({navigation}) => {
  const [stepIndex, setStepIndex] = useState(0);

  const steps = useMemo<WizardStep[]>(
    () => [
      {
        id: 'introduction',
        title: 'Get ready to enroll',
        description:
          'We will capture the QR code from the integration portal and exchange a proof token to bind this device. Make sure you have the enrollment QR visible on your workstation.',
        actionLabel: 'Begin',
      },
      {
        id: 'permissions',
        title: 'Enable camera access',
        description:
          'The camera is required to scan the enrollment QR code. Grant permission when prompted. You can also open the system settings later if you deny it by mistake.',
        actionLabel: 'Simulate Request',
        secondaryLabel: 'Learn more',
      },
      {
        id: 'scan',
        title: 'Scan the QR code',
        description:
          'Align the QR code within the frame. We decode the enrollmentId and proof token locally. Nothing is sent to Ezkey until the bind call succeeds.',
        actionLabel: 'Mock Scan Success',
      },
      {
        id: 'confirm',
        title: 'Review and finish',
        description: `You are about to bind ${MOCK_DEVICE_NAME} to your Ezkey enrollment. On the real flow, we generate an RSA key pair, store the private key in secure storage, and verify the proof token before activating.`,
        actionLabel: 'Finish',
        secondaryLabel: 'Back to Home',
      },
    ],
    [],
  );

  const currentStep = steps[stepIndex];
  const progress = (stepIndex + 1) / steps.length;

  const handlePrimary = useCallback(() => {
    if (stepIndex === steps.length - 1) {
      Alert.alert('Enrollment simulated', 'Returning to the home screen.');
      navigation.popToTop();
      return;
    }
    setStepIndex(index => Math.min(index + 1, steps.length - 1));
  }, [navigation, stepIndex, steps.length]);

  const handleSecondary = useCallback(() => {
    if (!currentStep.secondaryLabel) {
      return;
    }
    if (currentStep.id === 'permissions') {
      Alert.alert(
        'Why we need camera access',
        'The QR holds temporary enrollment credentials. The app never stores raw images; it only processes the encoded payload locally.',
      );
      return;
    }
    navigation.popToTop();
  }, [currentStep, navigation]);

  const handleBack = useCallback(() => {
    if (stepIndex === 0) {
      navigation.goBack();
      return;
    }
    setStepIndex(index => Math.max(index - 1, 0));
  }, [navigation, stepIndex]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={handleBack} style={styles.backButton}>
          <Text style={styles.backLabel}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Add enrollment</Text>
        <View style={styles.backButton} />
      </View>
      <View style={styles.progressTrack}>
        <View style={[styles.progressBar, {flex: progress}]} />
        <View style={[styles.progressRemaining, {flex: 1 - progress}]} />
      </View>
      <View style={styles.stepContainer}>
        <Text style={styles.stepTitle}>{currentStep.title}</Text>
        <Text style={styles.stepDescription}>{currentStep.description}</Text>
      </View>
      <TouchableOpacity onPress={handlePrimary} style={styles.primaryButton}>
        <Text style={styles.primaryLabel}>{currentStep.actionLabel}</Text>
      </TouchableOpacity>
      {currentStep.secondaryLabel ? (
        <TouchableOpacity onPress={handleSecondary} style={styles.secondaryButton}>
          <Text style={styles.secondaryLabel}>{currentStep.secondaryLabel}</Text>
        </TouchableOpacity>
      ) : null}
      <View style={styles.stepIndicator}>
        <Text style={styles.stepIndicatorText}>
          Step {stepIndex + 1} of {steps.length}
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    backgroundColor: '#0b0d11',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  backButton: {
    width: 72,
    height: 32,
    justifyContent: 'center',
  },
  backLabel: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  progressTrack: {
    flexDirection: 'row',
    height: 6,
    borderRadius: 3,
    overflow: 'hidden',
    backgroundColor: '#151923',
  },
  progressBar: {
    backgroundColor: '#61d095',
  },
  progressRemaining: {
    backgroundColor: 'transparent',
  },
  stepContainer: {
    flex: 1,
    paddingVertical: 32,
    gap: 16,
  },
  stepTitle: {
    fontSize: 24,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  stepDescription: {
    fontSize: 16,
    color: '#c2c8d5',
    lineHeight: 24,
  },
  primaryButton: {
    backgroundColor: '#61d095',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
  },
  primaryLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#0b0d11',
  },
  secondaryButton: {
    marginTop: 12,
    alignItems: 'center',
  },
  secondaryLabel: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  stepIndicator: {
    marginTop: 24,
    alignItems: 'center',
  },
  stepIndicatorText: {
    fontSize: 12,
    color: '#9aa3b6',
    letterSpacing: 0.6,
  },
});
