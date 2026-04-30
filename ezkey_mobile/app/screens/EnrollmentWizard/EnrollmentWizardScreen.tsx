/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentWizardScreen
 * Description: Guided enrollment experience that walks the user through QR scanning, challenge verification, and secure key generation.
 * Security Context: Implements the enrollment safeguards described in docs/features/AUTH_SECURITY.md by ensuring proof tokens are captured via QR, challenges are enforced, and device keys follow docs/CRYPTO.md.
 * @since 2025
 */

import React, {useCallback, useRef, useState} from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import axios from 'axios';
import {useCameraPermission} from 'react-native-vision-camera';
import {useSaveEnrollment} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {enrollmentsApi} from '../../services/api/enrollments';
import {instanceInfoApi} from '../../services/api/instanceInfo';
import {BindEnrollmentResponse} from '../../services/api/types';
import {cryptoService} from '../../services/crypto';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {EnrollmentScannerModal} from '../../components/EnrollmentScannerModal';
import {env} from '../../config/env';
import {integrationKeyAlgorithmBindError} from '../../utils/integrationKeyAlgorithm';
import {
  buildInstallationSummary,
  resolveEnrollmentAuthUrl,
} from '../../utils/installationMetadata';
import {validateAuthUrl} from '../../utils/urlValidation';
import {
  buildBindPayload,
  buildVerifyDevicePayload,
  buildVerifyResultPayload,
} from '../../services/crypto/enrollmentPayload';

type Props = StackScreenProps<RootStackParamList, 'EnrollmentWizard'>;

type EnrollmentDraft = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName?: string;
  tenantId?: number;
  tenantDescription?: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  integrationDescription?: string;
  enrollmentName?: string;
  deviceLabel?: string;
};

type EnrollmentInfoCardProps = {
  draft: EnrollmentDraft;
  showServerUrl?: boolean;
  serverUrl?: string;
  compact?: boolean;
};

/**
 * Displays user-relevant enrollment info from the bind response.
 * Omits technical fields (enrollmentId, publicKey, proofToken).
 * Uses Ezkey blue palette for subtle visual hierarchy.
 *
 * @since 2025
 */
const EnrollmentInfoCard: React.FC<EnrollmentInfoCardProps> = ({
  draft,
  showServerUrl,
  serverUrl,
  compact,
}) => (
  <View style={[styles.infoCard, compact && styles.infoCardCompact]}>
    <View style={[styles.infoCardHeader, compact && styles.infoCardHeaderCompact]}>
      <Text style={styles.infoCardTitle}>{draft.integrationName}</Text>
    </View>
    <View style={[styles.infoCardBody, compact && styles.infoCardBodyCompact]}>
      {draft.integrationDescription ? (
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>Description</Text>
          <Text style={styles.infoValue}>{draft.integrationDescription}</Text>
        </View>
      ) : null}
      {draft.tenantName ? (
        <>
          <View style={styles.infoDivider} />
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Organization</Text>
            <Text style={styles.infoValue}>{draft.tenantName}</Text>
          </View>
          {draft.tenantDescription ? (
            <Text style={styles.infoValueMuted}>{draft.tenantDescription}</Text>
          ) : null}
        </>
      ) : null}
      {draft.enrollmentName ? (
        <>
          <View style={styles.infoDivider} />
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Device</Text>
            <Text style={styles.infoValue}>{draft.enrollmentName}</Text>
          </View>
        </>
      ) : null}
      {showServerUrl && serverUrl ? (
        <>
          <View style={styles.infoDivider} />
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Server</Text>
            <Text style={styles.infoValueSmall}>{serverUrl}</Text>
          </View>
        </>
      ) : null}
    </View>
  </View>
);

type ChallengeCodeInputProps = {
  value: string;
  onChangeText: (text: string) => void;
  onClearError?: () => void;
  editable?: boolean;
};

const CHALLENGE_LENGTH = 6;

/**
 * Six-box challenge code input with paste support.
 * Hidden TextInput overlaid for keyboard; digits displayed in boxes.
 *
 * @since 2025
 */
const ChallengeCodeInput: React.FC<ChallengeCodeInputProps> = ({
  value,
  onChangeText,
  onClearError,
  editable = true,
}) => {
  const inputRef = useRef<TextInput>(null);
  const digits = value.split('').concat(Array(CHALLENGE_LENGTH).fill('')).slice(0, CHALLENGE_LENGTH);

  const handleChange = useCallback(
    (text: string) => {
      onClearError?.();
      const digitsOnly = text.replace(/[^0-9]/g, '');
      const next = digitsOnly.length > 1 ? digitsOnly.slice(0, CHALLENGE_LENGTH) : digitsOnly;
      onChangeText(next);
    },
    [onChangeText, onClearError],
  );

  return (
    <Pressable
      onPress={() => editable && inputRef.current?.focus()}
      style={styles.challengeContainer}
      accessibilityLabel="Challenge code input"
      accessibilityHint="Enter the 6-digit code shown in the admin console">
      <View style={styles.challengeBoxes}>
        {digits.map((digit, i) => (
          <View
            key={i}
            style={[
              styles.challengeBox,
              digit ? styles.challengeBoxFilled : undefined,
            ]}>
            <Text style={styles.challengeDigit}>{digit || ''}</Text>
          </View>
        ))}
      </View>
      <TextInput
        ref={inputRef}
        value={value}
        onChangeText={handleChange}
        keyboardType="number-pad"
        maxLength={CHALLENGE_LENGTH}
        editable={editable}
        caretHidden
        style={styles.challengeInputHidden}
      />
    </Pressable>
  );
};

/**
 * Walks the user through the Ezkey device enrollment workflow.
 *
 * @param navigation Stack navigation helper.
 * @since 2025
 */
export const EnrollmentWizardScreen: React.FC<Props> = ({navigation}) => {
  const {hasPermission: hasCameraPermission, requestPermission} = useCameraPermission();
  const [draft, setDraft] = useState<EnrollmentDraft | undefined>();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isBinding, setIsBinding] = useState(false);
  const [bindError, setBindError] = useState<string | undefined>();
  const [cameraError, setCameraError] = useState<string | undefined>();
  const [bindForm, setBindForm] = useState({
    enrollmentId: '',
    enrollmentProofToken: '',
  });
  const [enrollmentChallenge, setEnrollmentChallenge] = useState('');
  const [challengeError, setChallengeError] = useState<string | undefined>();
  const [scannerVisible, setScannerVisible] = useState(false);
  const [authUrl, setAuthUrl] = useState<string | undefined>();
  const saveEnrollment = useSaveEnrollment();

  const extractErrorMessage = useCallback((error: unknown) => {
    if (axios.isAxiosError(error)) {
      const data = error.response?.data as Record<string, unknown> | undefined;
      const message =
        (typeof data?.message === 'string' ? data.message : null) ??
        (typeof data?.detail === 'string' ? data.detail : null) ??
        (typeof data?.error === 'string' ? data.error : null) ??
        (typeof data?.code === 'string' ? data.code : null) ??
        error.message ??
        'Request failed.';
      return message;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'Unexpected error.';
  }, []);

  const buildDraft = useCallback(
    (
      response: BindEnrollmentResponse,
      request: {enrollmentId: string; enrollmentProofToken: string},
    ): EnrollmentDraft => {
      const rawId = response.enrollmentId ?? request.enrollmentId;
      const enrollmentId = String(rawId);
      return {
        id: enrollmentId,
        integrationId: enrollmentId,
        integrationName: response.integrationName ?? 'Integration',
        tenantName: response.tenantName ?? undefined,
        tenantId: response.tenantId,
        tenantDescription: response.tenantDescription,
        enrollmentProofToken: response.enrollmentProofToken ?? request.enrollmentProofToken,
        integrationPublicKey: response.integrationPublicKey,
        integrationDescription: response.integrationDescription,
        enrollmentName: response.enrollmentName,
        deviceLabel: response.enrollmentName,
      };
    },
    [],
  );

  const performBinding = useCallback(
    async (override?: {enrollmentId: string; enrollmentProofToken: string; authUrl?: string}) => {
      if (isBinding) {
        return;
      }
      const enrollmentId = (override?.enrollmentId ?? bindForm.enrollmentId).trim();
      const enrollmentProofToken = (override?.enrollmentProofToken ?? bindForm.enrollmentProofToken).trim();
      if (!enrollmentId || !enrollmentProofToken) {
        setScannerVisible(false);
        setBindError('Enrollment ID and proof token are required.');
        return;
      }
      const urlForBind =
        (override?.authUrl !== undefined ? override.authUrl : authUrl)?.trim() || undefined;
      const hasGlobalBase = Boolean(env.configuredApiBaseUrl?.trim());
      if (!urlForBind && !hasGlobalBase) {
        setScannerVisible(false);
        setBindError(
          'No Auth API URL: scan a QR that includes authUrl (set ezkey.qr.auth-base-url on the server), or set EZKEY_API_BASE_URL in .env and rebuild.',
        );
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
        }));
        const response = await enrollmentsApi.bind(
          {
            enrollmentId,
            enrollmentProofToken,
          },
          urlForBind,
        );
        const algoErr = integrationKeyAlgorithmBindError(response.integrationKeyAlgorithm);
        if (algoErr) {
          setBindError(algoErr);
          setScannerVisible(false);
          return;
        }
        const bindPayload = buildBindPayload(response);
        const bindSigOk = await cryptoService.verify(
          bindPayload,
          response.enrollmentBindPayloadSignedByIntegration,
          response.integrationPublicKey,
        );
        if (!bindSigOk) {
          setBindError('Could not verify server identity (integration signature).');
          setScannerVisible(false);
          return;
        }
        const nextDraft = buildDraft(response, {enrollmentId, enrollmentProofToken});
        setDraft(nextDraft);
        setScannerVisible(false);
      } catch (error) {
        setBindError(extractErrorMessage(error));
        setScannerVisible(false);
      } finally {
        setIsBinding(false);
      }
    },
    [authUrl, bindForm, buildDraft, extractErrorMessage, isBinding],
  );

  const finalizeEnrollment = useCallback(async () => {
    if (!draft) {
      Alert.alert('Missing scan', 'Scan the enrollment QR before finishing.');
      return;
    }
    const challengeResponse = enrollmentChallenge.trim();
    if (challengeResponse.length !== 6) {
      setChallengeError('Enrollment challenge must be 6 characters.');
      return;
    }
    const enrollmentId = draft.id.toString();
    const now = new Date().toISOString();
    const effectiveAuthUrl = resolveEnrollmentAuthUrl(authUrl);
    setIsSubmitting(true);
    try {
      // Ensure EC P-256 key pair exists for this enrollment (generates if needed)
      await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
      // Get EC P-256 public key for this enrollment
      const publicKey = await cryptoService.getPublicKey(enrollmentId);
      const devicePrivateKeyStorageTier =
        await cryptoService.getEnrollmentPrivateKeyStorageTier(enrollmentId);
      const challengeNum = Number(challengeResponse);
      const verifyDevicePayload = buildVerifyDevicePayload(
        draft.enrollmentProofToken,
        Number(draft.id),
        challengeNum,
        publicKey,
      );
      const proofTokenSigned = await cryptoService.sign(enrollmentId, verifyDevicePayload);
      const verifyResponse = await enrollmentsApi.verify({
        enrollmentId: draft.id,
        devicePublicKey: publicKey,
        enrollmentProofTokenSigned: proofTokenSigned,
        challengeResponse,
        devicePrivateKeyStorageTier,
      }, effectiveAuthUrl);
      const verifyResultPayload = buildVerifyResultPayload(
        draft.enrollmentProofToken,
        Number(draft.id),
        'VERIFIED',
        verifyResponse.enrollmentVerifyMessage,
      );
      const verifyResultOk = await cryptoService.verify(
        verifyResultPayload,
        verifyResponse.enrollmentVerifyPayloadSignedByIntegration,
        draft.integrationPublicKey,
      );
      if (!verifyResultOk) {
        setChallengeError('Could not verify enrollment result (integration signature).');
        setEnrollmentChallenge('');
        return;
      }
      let installationSummary =
        effectiveAuthUrl != null ? buildInstallationSummary(effectiveAuthUrl, undefined, now) : {};

      if (effectiveAuthUrl) {
        try {
          const instanceInfo = await instanceInfoApi.get(effectiveAuthUrl);
          installationSummary = buildInstallationSummary(effectiveAuthUrl, instanceInfo, now);
        } catch (error) {
          console.warn('[EnrollmentWizard] Failed to fetch installation metadata:', error);
        }
      }

      const record: StoredEnrollment = {
        id: draft.id,
        integrationId: draft.integrationId,
        integrationName: draft.integrationName,
        tenantName: draft.tenantName,
        tenantId: draft.tenantId,
        tenantDescription: draft.tenantDescription,
        createdAt: now,
        lastActivityAt: now,
        favorited: false,
        enrollmentProofToken: draft.enrollmentProofToken,
        enrollmentId: enrollmentId,
        integrationPublicKey: draft.integrationPublicKey,
        enrollmentName: draft.enrollmentName,
        deviceLabel: draft.deviceLabel,
        authUrl: effectiveAuthUrl,
        ...installationSummary,
      };
      await saveEnrollment.mutateAsync(record);
      setDraft(undefined);
      setEnrollmentChallenge('');
      navigation.popToTop();
    } catch (error) {
      const message = extractErrorMessage(error);
      setChallengeError(message);
      setEnrollmentChallenge('');
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
  ]);

  const handlePrimary = useCallback(() => {
    if (!draft) {
      setBindError(undefined);
      setCameraError(undefined);
      if (hasCameraPermission) {
        setScannerVisible(true);
      } else {
        requestPermission().then(granted => {
          if (granted) {
            setScannerVisible(true);
          } else {
            setCameraError('Camera access is required. Enable it in Settings to scan the QR code.');
          }
        });
      }
      return;
    }
    if (enrollmentChallenge.trim().length !== 6) {
      setChallengeError('Enrollment challenge must be 6 characters.');
      return;
    }
    setChallengeError(undefined);
    finalizeEnrollment().catch(() => {});
  }, [draft, enrollmentChallenge, finalizeEnrollment, hasCameraPermission, requestPermission]);

  const handleSecondary = useCallback(() => {
    if (draft) {
      if (!isBinding && !isSubmitting) {
        setDraft(undefined);
        setEnrollmentChallenge('');
        setChallengeError(undefined);
        navigation.popToTop();
      }
      return;
    }
    Alert.alert(
      'Why we need camera access',
      'The QR holds temporary enrollment credentials. The app never stores raw images; it only processes the encoded payload locally.',
    );
  }, [draft, isBinding, isSubmitting, navigation]);

  const handleBack = useCallback(() => {
    if (isSubmitting || isBinding) {
      return;
    }
    if (draft) {
      setChallengeError(undefined);
      setEnrollmentChallenge('');
      setDraft(undefined);
      return;
    }
    navigation.goBack();
  }, [draft, isBinding, isSubmitting, navigation]);

  const hasDraft = Boolean(draft);
  const challengeMissing = hasDraft && enrollmentChallenge.trim().length !== 6;
  const primaryDisabled =
    (hasDraft && isSubmitting) || (!hasDraft && isBinding) || challengeMissing;
  const secondaryDisabled = (hasDraft && isSubmitting) || (!hasDraft && isBinding);
  const primaryLabel = hasDraft
    ? isSubmitting
      ? 'Finishing…'
      : 'Complete enrollment'
    : isBinding
      ? 'Binding…'
      : 'Open scanner';
  const secondaryLabel = hasDraft ? 'Cancel' : 'Learn more';

  return (
    <>
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}>
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity onPress={handleBack} style={styles.backButton}>
            <Text style={styles.backLabel}>Back</Text>
          </TouchableOpacity>
          <View style={styles.backButton} />
        </View>
        <View style={styles.stepContainer}>
          <Text style={styles.flowSectionLabel}>Scan</Text>
          <Text style={styles.stepTitle}>Scan the QR code</Text>
          <Text style={styles.stepDescription}>
            Have the enrollment QR visible on your workstation. Tap Open scanner to use the camera.
          </Text>
          <View style={styles.scanInstructions}>
            {(bindError || cameraError) ? (
              <View style={styles.errorBanner}>
                <Text style={styles.errorBannerText}>{bindError ?? cameraError}</Text>
              </View>
            ) : null}
          </View>
          {hasDraft && draft ? (
            <>
              <View style={styles.flowDivider} />
              <Text style={styles.flowSectionLabel}>Verify</Text>
              <View style={styles.challengeSection}>
                <Text style={styles.challengeHeading}>Enter the 6-digit code from the admin console</Text>
                <Text style={styles.challengeHint}>
                  Tap Complete enrollment below to finish linking this device to {draft.integrationName}.
                </Text>
                <ChallengeCodeInput
                  value={enrollmentChallenge}
                  onChangeText={value => setEnrollmentChallenge(value)}
                  onClearError={() => setChallengeError(undefined)}
                  editable={!isSubmitting}
                />
                {challengeError ? (
                  <View style={styles.errorBanner}>
                    <Text style={styles.errorBannerText}>{challengeError}</Text>
                  </View>
                ) : null}
              </View>
              <Text style={styles.enrollmentDetailsLabel}>Enrollment details</Text>
              <EnrollmentInfoCard draft={draft} compact />
            </>
          ) : null}
        </View>
      </ScrollView>
      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.secondaryButton, secondaryDisabled ? styles.disabledButton : undefined]}
          onPress={handleSecondary}
          disabled={secondaryDisabled}>
          <Text style={styles.secondaryLabel}>{secondaryLabel}</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.primaryButton, primaryDisabled ? styles.disabledButton : undefined]}
          onPress={handlePrimary}
          disabled={primaryDisabled}>
          <Text style={styles.primaryLabel}>{primaryLabel}</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
      <EnrollmentScannerModal
        visible={scannerVisible}
        onDismiss={() => setScannerVisible(false)}
        onScanned={value => {
          if (__DEV__) {
            console.log('[EnrollmentWizard] Raw QR value:', JSON.stringify(value));
          }
          try {
            const parsed = parseQrPayload(value);
            if (__DEV__) {
              console.log('[EnrollmentWizard] Parsed QR payload:', JSON.stringify(parsed));
            }
            setAuthUrl(parsed.authUrl);
            setBindForm(() => ({
              enrollmentId: parsed.enrollmentId,
              enrollmentProofToken: parsed.enrollmentProofToken,
            }));
            setBindError(undefined);
            performBinding(parsed);
          } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            setScannerVisible(false);
            console.warn('[EnrollmentWizard] Invalid QR payload:', message, '| raw:', JSON.stringify(value));
            Alert.alert('Invalid QR', `The scanned code is not a valid Ezkey enrollment.\n\nDetails: ${message}`);
          }
        }}
      />
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0b0d11',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 24,
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
    color: '#5a9cf7',
    fontSize: 14,
    fontWeight: '500',
  },
  flowSectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#5a7aa8',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
  },
  flowDivider: {
    height: 1,
    backgroundColor: 'rgba(54, 115, 223, 0.12)',
    marginVertical: 24,
  },
  stepContainer: {
    flexGrow: 1,
    marginTop: 24,
  },
  challengeSection: {
    marginTop: 4,
    marginBottom: 16,
  },
  challengeHeading: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
    marginBottom: 8,
    lineHeight: 24,
  },
  challengeHint: {
    fontSize: 14,
    color: '#9aa3b6',
    marginBottom: 16,
    lineHeight: 20,
  },
  enrollmentDetailsLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#5a7aa8',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
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
  challengeContainer: {
    position: 'relative',
  },
  challengeBoxes: {
    flexDirection: 'row',
    gap: 8,
    justifyContent: 'center',
  },
  challengeBox: {
    width: 44,
    height: 52,
    borderRadius: 10,
    backgroundColor: '#151923',
    borderWidth: 3,
    borderColor: 'rgba(54, 115, 223, 0.5)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  challengeBoxFilled: {
    borderColor: 'rgba(54, 115, 223, 0.85)',
  },
  challengeDigit: {
    fontSize: 28,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  challengeInputHidden: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    opacity: 0,
    fontSize: 1,
  },
  formError: {
    fontSize: 13,
    color: '#ff7878',
  },
  errorBanner: {
    backgroundColor: 'rgba(255, 120, 120, 0.15)',
    borderLeftWidth: 4,
    borderLeftColor: '#ff6666',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginTop: 4,
  },
  errorBannerText: {
    fontSize: 15,
    fontWeight: '500',
    color: '#ff7878',
    lineHeight: 22,
  },
  scanInstructions: {
    gap: 12,
  },
  infoCard: {
    marginTop: 24,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: '#0f1628',
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.2)',
  },
  infoCardCompact: {
    marginTop: 0,
  },
  infoCardHeader: {
    backgroundColor: 'rgba(18, 39, 92, 0.6)',
    paddingVertical: 14,
    paddingHorizontal: 18,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(54, 115, 223, 0.15)',
  },
  infoCardHeaderCompact: {
    paddingVertical: 10,
    paddingHorizontal: 14,
  },
  infoCardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#d6e6ff',
  },
  infoCardBody: {
    padding: 18,
    gap: 10,
  },
  infoCardBodyCompact: {
    padding: 14,
    gap: 8,
  },
  infoRow: {
    gap: 4,
  },
  infoLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: '#5a7aa8',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  infoValue: {
    fontSize: 15,
    color: '#f4f7ff',
    lineHeight: 22,
  },
  infoValueMuted: {
    fontSize: 13,
    color: '#9aa3b6',
    lineHeight: 20,
    marginTop: -4,
  },
  infoValueSmall: {
    fontSize: 12,
    color: '#5a9cf7',
    flexShrink: 1,
  },
  infoDivider: {
    height: 1,
    backgroundColor: 'rgba(54, 115, 223, 0.12)',
    marginVertical: 8,
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
    backgroundColor: '#3076df',
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
    color: '#ffffff',
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
    const json = JSON.parse(trimmed) as {
      enrollmentId?: string | number;
      enrollmentProofToken?: string;
      authUrl?: string;
    };
    if (json.enrollmentId && json.enrollmentProofToken) {
      if (json.authUrl !== undefined && validateAuthUrl(json.authUrl) == null) {
        throw new Error('Invalid Auth API URL in QR payload.');
      }

      return {
        enrollmentId: String(json.enrollmentId),
        enrollmentProofToken: String(json.enrollmentProofToken),
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

