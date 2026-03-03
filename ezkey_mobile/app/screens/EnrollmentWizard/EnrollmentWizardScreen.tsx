/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentWizardScreen
 * Description: Guided enrollment experience that walks the user through QR scanning, challenge verification, and secure key generation.
 * Security Context: Implements the enrollment safeguards described in docs/features/AUTH_SECURITY.md by ensuring proof tokens are captured via QR, challenges are enforced, and Ed25519 keys follow docs/CRYPTO.md.
 * @since 2025
 */

import React, {useCallback, useMemo, useState} from 'react';
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
import {useCameraPermission} from 'react-native-vision-camera';
import {useSaveEnrollment} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {enrollmentsApi} from '../../services/api/enrollments';
import {BindEnrollmentResponse, EnrollmentStatus} from '../../services/api/types';
import {cryptoService} from '../../services/crypto';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {EnrollmentScannerModal} from '../../components/EnrollmentScannerModal';
import {validateAuthUrl} from '../../utils/urlValidation';

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
  tenantId?: number;
  tenantDescription?: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  logoUri?: string;
  integrationDescription?: string;
  enrollmentName?: string;
  deviceLabel?: string;
  status: EnrollmentStatus;
};

const MOCK_DEVICE_NAME = 'Pixel 7 Pro';

/**
 * Walks the user through the Ezkey device enrollment workflow.
 *
 * @param navigation Stack navigation helper.
 * @since 2025
 */
export const EnrollmentWizardScreen: React.FC<Props> = ({navigation}) => {
  const {hasPermission: hasCameraPermission, requestPermission} = useCameraPermission();
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
  const [scannerVisible, setScannerVisible] = useState(false);
  const [authUrl, setAuthUrl] = useState<string | undefined>();
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
        description: hasCameraPermission
          ? 'Camera permission is already granted. Continue to scan the enrollment QR code.'
          : 'The camera is required to scan the enrollment QR code. Grant permission when prompted. You can also open the system settings later if you deny it by mistake.',
        actionLabel: hasCameraPermission ? 'Start scanning' : 'Grant permission',
        secondaryLabel: 'Learn more',
      },
      {
        id: 'scan',
        title: 'Scan the QR code',
        description: 'Align the enrollment QR code within the frame to populate the enrollment details.',
        actionLabel: 'Open scanner',
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
          ? `You are about to bind ${MOCK_DEVICE_NAME} to ${draft.integrationName}. We will generate an Ed25519 key pair derived from the root key, and verify the proof token before activating.`
          : `You are about to bind ${MOCK_DEVICE_NAME} to your Ezkey enrollment. On the real flow, we generate an Ed25519 key pair derived from the root key, and verify the proof token before activating.`,
        actionLabel: 'Finish',
        secondaryLabel: 'Back to Home',
      },
    ],
    [draft, hasCameraPermission],
  );

  const currentStep = steps[stepIndex];
  const progress = (stepIndex + 1) / steps.length;

  const buildDraft = useCallback(
    (
      response: BindEnrollmentResponse,
      request: {enrollmentId: string; enrollmentProofToken: string; language?: string},
    ): EnrollmentDraft => {
      const rawId = response.enrollmentId ?? request.enrollmentId;
      const enrollmentId = String(rawId);
      return {
        id: enrollmentId,
        integrationId: enrollmentId,
        integrationName: response.integrationName ?? 'Integration',
        tenantName:
          response.tenantName ?? response.integrationDescription ?? 'Your organization',
        tenantId: response.tenantId,
        tenantDescription: response.tenantDescription,
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
    async (override?: {enrollmentId: string; enrollmentProofToken: string; language?: string; authUrl?: string}) => {
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
        }, authUrl);
        const nextDraft = buildDraft(response, {enrollmentId, enrollmentProofToken, language});
        setDraft(nextDraft);
        setStepIndex(() => {
          const challengeIndex = steps.findIndex(step => step.id === 'challenge');
          return challengeIndex >= 0 ? challengeIndex : 0;
        });
        setScannerVisible(false);
      } catch (error) {
        setBindError(extractErrorMessage(error));
      } finally {
        setIsBinding(false);
      }
    },
    [authUrl, bindForm, buildDraft, extractErrorMessage, isBinding, steps],
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
    const enrollmentId = draft.id.toString();
    const now = new Date().toISOString();
    setIsSubmitting(true);
    try {
      // Ensure EC P-256 key pair exists for this enrollment (generates if needed)
      await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
      // Get EC P-256 public key for this enrollment
      const publicKey = await cryptoService.getPublicKey(enrollmentId);
      // Sign the proof token with EC P-256 (ECDSA-SHA256)
      const proofTokenSigned = await cryptoService.sign(enrollmentId, draft.enrollmentProofToken);
      const verifyResponse = await enrollmentsApi.verify({
        enrollmentId: draft.id,
        devicePublicKey: publicKey,
        enrollmentProofTokenSigned: proofTokenSigned,
        challengeResponse,
      }, authUrl);
      const status: EnrollmentStatus = verifyResponse.active ? 'active' : 'pending';
      const record: StoredEnrollment = {
        id: draft.id,
        integrationId: draft.integrationId,
        integrationName: draft.integrationName,
        tenantName: draft.tenantName,
        tenantId: draft.tenantId,
        tenantDescription: draft.tenantDescription,
        createdAt: now,
        lastActivityAt: now,
        status,
        logoUri:
          draft.logoUri ??
          `https://placehold.co/128x128?text=${draft.integrationName.charAt(0).toUpperCase()}`,
        favorited: false,
        enrollmentProofToken: draft.enrollmentProofToken,
        enrollmentId: enrollmentId,
        integrationPublicKey: draft.integrationPublicKey,
        enrollmentName: draft.enrollmentName,
        deviceLabel: draft.deviceLabel,
        authUrl,
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
    authUrl,
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
    if (step.id === 'permissions') {
      const proceed = () => {
        setStepIndex(index => Math.min(index + 1, steps.length - 1));
        setScannerVisible(true);
      };
      if (hasCameraPermission) {
        proceed();
      } else {
        requestPermission().then(granted => {
          if (granted) {
            proceed();
          }
        });
      }
      return;
    }
    if (step.id === 'scan') {
      setScannerVisible(true);
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
    hasCameraPermission,
    performBinding,
    requestPermission,
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
          <View style={styles.scanInstructions}>
            <Text style={styles.scanHint}>
              Tap below to open the camera and scan the enrollment QR code. We will automatically fill in the details
              once the scan succeeds.
            </Text>
            {bindError ? <Text style={styles.formError}>{bindError}</Text> : null}
            <TouchableOpacity
              style={styles.scanButton}
              onPress={() => {
                if (isBinding) {
                  return;
                }
                setScannerVisible(true);
              }}>
              <Text style={styles.scanButtonLabel}>Open scanner</Text>
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
            </View>
            <Text style={styles.inputLabel}>Challenge code</Text>
            <TextInput
              value={enrollmentChallenge}
              onChangeText={value => {
                setChallengeError(undefined);
                setEnrollmentChallenge(value.replace(/[^0-9]/g, '').slice(0, 6));
              }}
              keyboardType="number-pad"
              style={styles.input}
              placeholder="000000"
              placeholderTextColor="#5f6780"
              editable={!isSubmitting}
            />
            {challengeError ? <Text style={styles.formError}>{challengeError}</Text> : null}
          </>
        ) : null}
        {currentStep.id === 'confirm' && draft ? (
          <View style={styles.summaryCard}>
            <Text style={styles.summaryTitle}>{draft.integrationName}</Text>
            <Text style={styles.summarySubtitle}>{draft.tenantName}</Text>
            {authUrl ? (
              <View style={styles.serverRow}>
                <Text style={styles.serverLabel}>Server</Text>
                <Text style={styles.serverValue}>{authUrl}</Text>
              </View>
            ) : null}
            <Text style={styles.summaryMeta}>Status: {draft.status.toUpperCase()}</Text>
            <Text style={styles.summaryMeta}>
              Created {new Date().toLocaleString(undefined, {dateStyle: 'medium', timeStyle: 'short'})}
            </Text>
          </View>
        ) : null}
      </View>
      <View style={styles.actions}>
        {currentStep.secondaryLabel ? (
          <TouchableOpacity
            style={[styles.secondaryButton, secondaryDisabled ? styles.disabledButton : undefined]}
            onPress={handleSecondary}
            disabled={secondaryDisabled}>
            <Text style={styles.secondaryLabel}>{currentStep.secondaryLabel}</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.secondaryButtonPlaceholder} />
        )}
        <TouchableOpacity
          style={[styles.primaryButton, primaryDisabled ? styles.disabledButton : undefined]}
          onPress={handlePrimary}
          disabled={primaryDisabled}>
          <Text style={styles.primaryLabel}>{primaryLabel}</Text>
        </TouchableOpacity>
      </View>

      <EnrollmentScannerModal
        visible={scannerVisible}
        onDismiss={() => setScannerVisible(false)}
        onScanned={value => {
          console.log('[EnrollmentWizard] Raw QR value:', JSON.stringify(value));
          try {
            const parsed = parseQrPayload(value);
            console.log('[EnrollmentWizard] Parsed QR payload:', JSON.stringify(parsed));
            setAuthUrl(parsed.authUrl);
            setBindForm(prev => ({
              enrollmentId: parsed.enrollmentId,
              enrollmentProofToken: parsed.enrollmentProofToken,
              language: parsed.language ?? prev.language ?? 'en',
            }));
            setBindError(undefined);
            performBinding(parsed);
          } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            console.warn('[EnrollmentWizard] Invalid QR payload:', message, '| raw:', JSON.stringify(value));
            Alert.alert('Invalid QR', `The scanned code is not a valid Ezkey enrollment.\n\nDetails: ${message}`);
          }
        }}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0b0d11',
    paddingHorizontal: 20,
    paddingTop: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  backButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  backLabel: {
    color: '#61d095',
    fontSize: 14,
    fontWeight: '500',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  progressTrack: {
    flexDirection: 'row',
    height: 6,
    borderRadius: 3,
    backgroundColor: '#1c2230',
    overflow: 'hidden',
    marginTop: 16,
  },
  progressBar: {
    backgroundColor: '#61d095',
  },
  progressRemaining: {
    backgroundColor: 'transparent',
  },
  stepContainer: {
    flex: 1,
    marginTop: 24,
  },
  stepTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  stepDescription: {
    fontSize: 14,
    color: '#c2c8d5',
    marginTop: 8,
    marginBottom: 24,
  },
  form: {
    width: '100%',
    gap: 12,
  },
  inputLabel: {
    fontSize: 13,
    fontWeight: '500',
    color: '#c2c8d5',
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
  scanInstructions: {
    gap: 12,
  },
  scanHint: {
    fontSize: 14,
    color: '#c2c8d5',
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
  serverRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  serverLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#9aa3b6',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  serverValue: {
    fontSize: 13,
    color: '#61d095',
    flexShrink: 1,
  },
  scanButton: {
    marginTop: 12,
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
  actions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 16,
  },
  primaryButton: {
    flex: 1,
    backgroundColor: '#61d095',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  secondaryButton: {
    flex: 1,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#5f6780',
    paddingVertical: 14,
    alignItems: 'center',
  },
  secondaryButtonPlaceholder: {
    flex: 1,
  },
  disabledButton: {
    opacity: 0.6,
  },
  secondaryLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  primaryLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#0b0d11',
  },
});

/**
 * Parsed result of an enrollment QR code payload.
 *
 * @since 2025
 */
type QrPayload = {
  enrollmentId: string;
  enrollmentProofToken: string;
  language?: string;
  /** Validated Auth API base URL when present in the QR code. */
  authUrl?: string;
};

/**
 * Parses the enrollment QR payload which may be JSON or a pipe-delimited fallback.
 *
 * The JSON format is the primary format and may include an `authUrl` field pointing to the Ezkey Auth API
 * host for this enrollment. When present the URL is validated (HTTPS enforced, dev loopback tolerated).
 *
 * The pipe-delimited format (`enrollmentId|enrollmentProofToken`) is kept for backward compatibility and
 * does not carry an `authUrl`; the global default from `env.apiBaseUrl` will be used instead.
 *
 * The function enforces the payload constraints described in `docs/ENDPOINT.md` ensuring we extract proof tokens
 * without introducing alternate parsing paths that could weaken enrollment verification.
 *
 * @param value Raw QR code payload.
 * @return Structured enrollment payload including the optional `authUrl`.
 * @throws Error when the payload does not contain the expected fields.
 * @since 2025
 */
const parseQrPayload = (value: string): QrPayload => {
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
        authUrl: validateAuthUrl(json.authUrl),
      };
    }
  } catch {
    // ignore and try pipe format
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

