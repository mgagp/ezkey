import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  Alert,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import axios from 'axios';
import {Buffer} from 'buffer';
import {useSaveEnrollment} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {enrollmentsApi} from '../../services/api/enrollments';
import {BindEnrollmentResponse, EnrollmentStatus} from '../../services/api/types';
import {cryptoService} from '../../services/crypto';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {RouteProp, useRoute} from '@react-navigation/native';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentWizard'>;

type WizardStep = {
  id: string;
  title: string;
  description: string;
  actionLabel: string;
  secondaryLabel?: string;
};

type EnrollmentDraft = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  logoUri?: string;
  integrationDescription?: string;
  enrollmentName?: string;
  deviceLabel?: string;
  status: EnrollmentStatus;
};

const MOCK_DEVICE_NAME = 'Pixel 7 Pro';

export const EnrollmentWizardScreen: React.FC<Props> = ({navigation}) => {
  const route = useRoute<RouteProp<RootStackParamList, 'EnrollmentWizard'>>();
  const [stepIndex, setStepIndex] = useState(0);
  const [draft, setDraft] = useState<EnrollmentDraft | undefined>();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isBinding, setIsBinding] = useState(false);
  const [bindError, setBindError] = useState<string | undefined>();
  const [bindForm, setBindForm] = useState({
    enrollmentId: '',
    enrollmentProofToken: '',
    language: 'en',
  });
  const [enrollmentChallenge, setEnrollmentChallenge] = useState('');
  const [challengeError, setChallengeError] = useState<string | undefined>();
  const saveEnrollment = useSaveEnrollment();

  const extractErrorMessage = useCallback((error: unknown) => {
    if (axios.isAxiosError(error)) {
      const message =
        error.response?.data?.message ??
        error.response?.data?.error ??
        error.message ??
        'Request failed.';
      return message;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'Unexpected error.';
  }, []);

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
          'Align the QR code within the frame. For now you can paste the enrollment ID and proof token manually to simulate the scan result.',
        actionLabel: 'Bind enrollment',
      },
      {
        id: 'challenge',
        title: 'Enter enrollment challenge',
        description: draft
          ? `Review ${draft.integrationName} and enter the 6-digit enrollment challenge displayed in the admin console.`
          : 'Review the enrollment details and enter the 6-digit challenge shown in the admin console.',
        actionLabel: 'Continue',
        secondaryLabel: 'Cancel',
      },
      {
        id: 'confirm',
        title: 'Review and finish',
        description: draft
          ? `You are about to bind ${MOCK_DEVICE_NAME} to ${draft.integrationName}. We will generate an RSA key pair, store the private key securely, and verify the proof token before activating.`
          : `You are about to bind ${MOCK_DEVICE_NAME} to your Ezkey enrollment. On the real flow, we generate an RSA key pair, store the private key in secure storage, and verify the proof token before activating.`,
        actionLabel: 'Finish',
        secondaryLabel: 'Back to Home',
      },
    ],
    [draft],
  );

  const currentStep = steps[stepIndex];
  const progress = (stepIndex + 1) / steps.length;

  const buildDraft = useCallback(
    (
      response: BindEnrollmentResponse,
      request: {enrollmentId: string; enrollmentProofToken: string; language?: string},
    ): EnrollmentDraft => {
      const enrollmentId = response.enrollmentId ?? request.enrollmentId;
      return {
        id: enrollmentId,
        integrationId: enrollmentId,
        integrationName: response.integrationName ?? 'Integration',
        tenantName: response.integrationDescription ?? 'Your organization',
        enrollmentProofToken: response.enrollmentProofToken ?? request.enrollmentProofToken,
        integrationPublicKey: response.integrationPublicKey,
        logoUri: response.integrationLogo,
        integrationDescription: response.integrationDescription,
        enrollmentName: response.enrollmentName,
        deviceLabel: response.enrollmentName,
        status: 'pending',
      };
    },
    [],
  );

  const performBinding = useCallback(
    async (override?: {enrollmentId: string; enrollmentProofToken: string; language?: string}) => {
    if (isBinding) {
      return;
    }
      const enrollmentId = (override?.enrollmentId ?? bindForm.enrollmentId).trim();
      const enrollmentProofToken = (override?.enrollmentProofToken ?? bindForm.enrollmentProofToken).trim();
      const language = (override?.language ?? bindForm.language).trim() || undefined;
    if (!enrollmentId || !enrollmentProofToken) {
      setBindError('Enrollment ID and proof token are required.');
      return;
    }
    setBindError(undefined);
    setIsBinding(true);
    setDraft(undefined);
    setEnrollmentChallenge('');
    setChallengeError(undefined);
    try {
      setBindForm(previous => ({
        ...previous,
        enrollmentId,
        enrollmentProofToken,
        language: language ?? previous.language,
      }));
      const response = await enrollmentsApi.bind({
        enrollmentId,
        enrollmentProofToken,
        language,
      });
      const nextDraft = buildDraft(response, {
        enrollmentId,
        enrollmentProofToken,
        language,
      });
      setDraft(nextDraft);
      setStepIndex(index => Math.min(index + 1, steps.length - 1));
    } catch (error) {
      setBindError(extractErrorMessage(error));
    } finally {
      setIsBinding(false);
    }
    },
    [bindForm, buildDraft, extractErrorMessage, isBinding, steps.length],
  );

  const finalizeEnrollment = useCallback(async () => {
    if (!draft) {
      Alert.alert('Missing scan', 'Scan the enrollment QR before finishing.');
      return;
    }
    const challengeResponse = enrollmentChallenge.trim();
    if (challengeResponse.length !== 6) {
      setChallengeError('Enrollment challenge must be 6 characters.');
      const challengeIndex = steps.findIndex(step => step.id === 'challenge');
      if (challengeIndex >= 0) {
        setStepIndex(challengeIndex);
      }
      return;
    }
    const alias = `device-${draft.id}`;
    const now = new Date().toISOString();
    setIsSubmitting(true);
    try {
      const publicKey = await cryptoService.ensureKeyPair(alias);
      const proofTokenBase64 = Buffer.from(draft.enrollmentProofToken, 'utf-8').toString('base64');
      const proofTokenSigned = await cryptoService.sign(alias, proofTokenBase64);
      const verifyResponse = await enrollmentsApi.verify({
        enrollmentId: draft.id,
        devicePublicKey: publicKey,
        enrollmentProofTokenSigned: proofTokenSigned,
        challengeResponse,
      });
      const status: EnrollmentStatus = verifyResponse.active ? 'active' : 'pending';
      const record: StoredEnrollment = {
        id: draft.id,
        integrationId: draft.integrationId,
        integrationName: draft.integrationName,
        tenantName: draft.tenantName,
        createdAt: now,
        lastActivityAt: now,
        status,
        logoUri:
          draft.logoUri ??
          `https://placehold.co/128x128?text=${draft.integrationName.charAt(0).toUpperCase()}`,
        favorited: false,
        enrollmentProofToken: draft.enrollmentProofToken,
        deviceAlias: alias,
        integrationPublicKey: draft.integrationPublicKey,
        enrollmentName: draft.enrollmentName,
        deviceLabel: draft.deviceLabel,
      };
      await saveEnrollment.mutateAsync(record);
      const successMessage = verifyResponse.active
        ? `${draft.integrationName} is now available.`
        : `${draft.integrationName} was saved in pending state.`;
      Alert.alert('Enrollment completed', successMessage);
      setDraft(undefined);
      setEnrollmentChallenge('');
      navigation.popToTop();
    } catch (error) {
      console.error('[EnrollmentWizard] Failed to finalize enrollment', error);
      Alert.alert('Enrollment failed', extractErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }, [
    draft,
    enrollmentChallenge,
    extractErrorMessage,
    navigation,
    saveEnrollment,
    steps,
  ]);

  const handlePrimary = useCallback(() => {
    const step = steps[stepIndex];
    if (step.id === 'confirm') {
      finalizeEnrollment();
      return;
    }
    if (step.id === 'scan') {
      performBinding();
      return;
    }
    if (step.id === 'challenge') {
      if (enrollmentChallenge.trim().length !== 6) {
        setChallengeError('Enrollment challenge must be 6 characters.');
        return;
      }
      setChallengeError(undefined);
    }
    setStepIndex(index => Math.min(index + 1, steps.length - 1));
  }, [
    enrollmentChallenge,
    finalizeEnrollment,
    performBinding,
    stepIndex,
    steps,
  ]);

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
    if (currentStep.id === 'challenge') {
      if (!isBinding) {
        setDraft(undefined);
        setEnrollmentChallenge('');
        setChallengeError(undefined);
        navigation.popToTop();
      }
      return;
    }
    if (currentStep.id === 'scan' && isBinding) {
      return;
    }
    if (!isSubmitting) {
      navigation.popToTop();
    }
  }, [currentStep, isBinding, isSubmitting, navigation]);

  const handleBack = useCallback(() => {
  useEffect(() => {
    const scanned = route.params?.scanned;
    if (!scanned) {
      return;
    }
    setBindForm(prev => ({
      enrollmentId: scanned.enrollmentId,
      enrollmentProofToken: scanned.enrollmentProofToken,
      language: scanned.language ?? prev.language ?? 'en',
    }));
    setBindError(undefined);
    performBinding(scanned).finally(() => {
      navigation.setParams({scanned: undefined});
    });
  }, [navigation, performBinding, route.params?.scanned]);
    if (stepIndex === 0) {
      navigation.goBack();
      return;
    }
    if (isSubmitting || isBinding) {
      return;
    }
    const step = steps[stepIndex];
    if (step.id === 'confirm') {
      setDraft(undefined);
    }
    if (step.id === 'challenge') {
      setChallengeError(undefined);
      setEnrollmentChallenge('');
      setDraft(undefined);
    }
    setStepIndex(index => Math.max(index - 1, 0));
  }, [isBinding, isSubmitting, navigation, stepIndex, steps]);

  const challengeMissing = currentStep.id === 'challenge' && enrollmentChallenge.trim().length !== 6;
  const primaryDisabled =
    (currentStep.id === 'confirm' && isSubmitting) ||
    (currentStep.id === 'scan' && isBinding) ||
    challengeMissing;
  const secondaryDisabled =
    (currentStep.id === 'confirm' && isSubmitting) ||
    (currentStep.id === 'scan' && isBinding);
  const primaryLabel =
    currentStep.id === 'confirm'
      ? isSubmitting
        ? 'Finishing…'
        : currentStep.actionLabel
      : currentStep.id === 'scan' && isBinding
      ? 'Binding…'
      : currentStep.actionLabel;

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
        {currentStep.id === 'scan' ? (
          <View style={styles.form}>
            <Text style={styles.inputLabel}>Enrollment ID</Text>
            <TextInput
              value={bindForm.enrollmentId}
              onChangeText={value =>
                setBindForm(previous => {
                  setBindError(undefined);
                  return {...previous, enrollmentId: value};
                })
              }
              autoCapitalize="none"
              autoCorrect={false}
              style={styles.input}
              placeholder="e.g. 123"
              placeholderTextColor="#5f6780"
              editable={!isBinding}
            />
            <Text style={styles.inputLabel}>Enrollment proof token</Text>
            <TextInput
              value={bindForm.enrollmentProofToken}
              onChangeText={value =>
                setBindForm(previous => {
                  setBindError(undefined);
                  return {...previous, enrollmentProofToken: value};
                })
              }
              autoCapitalize="characters"
              autoCorrect={false}
              style={styles.input}
              placeholder="EZK-XXXX-XXXX"
              placeholderTextColor="#5f6780"
              editable={!isBinding}
            />
            <Text style={styles.inputLabel}>Language (optional)</Text>
            <TextInput
              value={bindForm.language}
              onChangeText={value =>
                setBindForm(previous => {
                  setBindError(undefined);
                  return {...previous, language: value};
                })
              }
              autoCapitalize="none"
              autoCorrect={false}
              style={styles.input}
              placeholder="en"
              placeholderTextColor="#5f6780"
              editable={!isBinding}
            />
            {bindError ? <Text style={styles.formError}>{bindError}</Text> : null}
            <TouchableOpacity
              style={styles.scanButton}
              onPress={() => navigation.navigate('EnrollmentScanner')}>
              <Text style={styles.scanButtonLabel}>Scan QR code with camera</Text>
            </TouchableOpacity>
          </View>
        ) : null}
        {currentStep.id === 'challenge' && draft ? (
          <>
            <View style={styles.summaryCard}>
              <Text style={styles.summaryTitle}>{draft.integrationName}</Text>
              {draft.integrationDescription ? (
                <Text style={styles.summarySubtitle}>{draft.integrationDescription}</Text>
              ) : null}
              <Text style={styles.summaryMeta}>Enrollment ID: {draft.id}</Text>
            {draft.enrollmentName ? (
              <Text style={styles.summaryMeta}>Device label: {draft.enrollmentName}</Text>
              ) : null}
            </View>
            <View style={styles.form}>
              <Text style={styles.inputLabel}>Enrollment challenge</Text>
              <TextInput
                value={enrollmentChallenge}
                onChangeText={value => {
                  setEnrollmentChallenge(value.replace(/[^0-9A-Za-z]/g, '').slice(0, 6).toUpperCase());
                  setChallengeError(undefined);
                }}
                autoCapitalize="characters"
                autoCorrect={false}
                style={styles.input}
                placeholder="e.g. A1B2C3"
                placeholderTextColor="#5f6780"
                editable={!isSubmitting}
              />
              {challengeError ? <Text style={styles.formError}>{challengeError}</Text> : null}
            </View>
          </>
        ) : null}
        {currentStep.id === 'confirm' && draft ? (
          <View style={styles.summaryCard}>
            <Text style={styles.summaryTitle}>{draft.integrationName}</Text>
            {draft.integrationDescription ? (
              <Text style={styles.summarySubtitle}>{draft.integrationDescription}</Text>
            ) : null}
            <Text style={styles.summaryMeta}>Enrollment ID: {draft.id}</Text>
            <Text style={styles.summaryMeta}>Alias: {`device-${draft.id}`}</Text>
            {enrollmentChallenge ? (
              <Text style={styles.summaryMeta}>Enrollment challenge: {enrollmentChallenge}</Text>
            ) : null}
          </View>
        ) : null}
      </View>
      <TouchableOpacity
        onPress={handlePrimary}
        style={[styles.primaryButton, primaryDisabled ? styles.disabledButton : undefined]}
        disabled={primaryDisabled}>
        <Text style={styles.primaryLabel}>{primaryLabel}</Text>
      </TouchableOpacity>
      {currentStep.secondaryLabel ? (
        <TouchableOpacity
          onPress={handleSecondary}
          style={[styles.secondaryButton, secondaryDisabled ? styles.disabledButton : undefined]}
          disabled={secondaryDisabled}>
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
  disabledButton: {
    opacity: 0.6,
  },
  secondaryLabel: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  form: {
    width: '100%',
    marginTop: 24,
    gap: 12,
  },
  inputLabel: {
    fontSize: 13,
    color: '#9aa3b6',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  input: {
    backgroundColor: '#151923',
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 16,
    color: '#f4f7ff',
    fontSize: 16,
  },
  formError: {
    fontSize: 13,
    color: '#ff7878',
  },
  scanButton: {
    marginTop: 8,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#61d095',
    paddingVertical: 12,
    alignItems: 'center',
  },
  scanButtonLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#61d095',
  },
  summaryCard: {
    marginTop: 24,
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    gap: 8,
  },
  summaryTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  summarySubtitle: {
    fontSize: 14,
    color: '#c2c8d5',
  },
  summaryMeta: {
    fontSize: 12,
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
