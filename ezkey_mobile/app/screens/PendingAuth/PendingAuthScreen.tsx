/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: PendingAuthScreen
 * Description: React Native screen orchestrating the device-side pending and respond flows.
 * Security Context: Embeds the polling model, proof token usage, and read-once semantics described in
 *                   docs/features/AUTH_SECURITY.md to keep user actions intentional and replay resistant.
 * UX: Approve and Deny only — there is no mobile "cancel" API; leaving the screen without responding
 *     relies on server-side TTL expiry (see Auth API / Admin batch expiry). Do not add a Cancel
 *     button that implies a distinct server action without an endpoint.
 * @since 2025
 */

import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import axios from 'axios';
import {Buffer} from 'buffer';
import {useTranslation} from 'react-i18next';
import {env} from '../../config/env';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentById, useMarkEnrollmentPendingChecked} from '../../hooks/useEnrollments';
import {authAttemptsApi} from '../../services/api/authAttempts';
import {
  buildPendingPayload,
  buildRespondPayload,
  buildRespondResultPayload,
} from '../../services/crypto/authAttemptPayload';
import {cryptoService} from '../../services/crypto';
import {PendingAttempt} from '../../services/pendingAuth/types';
import {generateProofToken} from '../../utils/generateProofToken';
import {sha256HexUtf8} from '../../utils/sha256HexUtf8';
import {useEnrollmentStore} from '../../state/enrollmentStore';

type Props = NativeStackScreenProps<RootStackParamList, 'PendingAuth'>;

const AUTH_CHALLENGE_LENGTH = 2;

type AuthChallengeCodeInputProps = {
  value: string;
  onChangeText: (text: string) => void;
  onClearError?: () => void;
  editable?: boolean;
};

/**
 * Two-box challenge code input with paste support.
 * Same pattern as enrollment 6-digit input for consistency.
 *
 * @since 2025
 */
const AuthChallengeCodeInput: React.FC<AuthChallengeCodeInputProps> = ({
  value,
  onChangeText,
  onClearError,
  editable = true,
}) => {
  const {t} = useTranslation();
  const inputRef = useRef<TextInput>(null);
  const digits = value.split('').concat(Array(AUTH_CHALLENGE_LENGTH).fill('')).slice(0, AUTH_CHALLENGE_LENGTH);

  const handleChange = useCallback(
    (text: string) => {
      onClearError?.();
      const digitsOnly = text.replace(/[^0-9]/g, '');
      const next = digitsOnly.length > 1 ? digitsOnly.slice(0, AUTH_CHALLENGE_LENGTH) : digitsOnly;
      onChangeText(next);
    },
    [onChangeText, onClearError],
  );

  return (
    <Pressable
      onPress={() => editable && inputRef.current?.focus()}
      style={styles.challengeContainer}
      accessibilityLabel={t('pendingAuth.challengeInput')}
      accessibilityHint={t('pendingAuth.challengeInputHint')}>
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
        maxLength={AUTH_CHALLENGE_LENGTH}
        editable={editable}
        caretHidden
        style={styles.challengeInputHidden}
      />
    </Pressable>
  );
};

/**
 * Presents the current pending authentication attempt for a selected enrollment and enables the user to accept or deny it.
 *
 * - Builds each `deviceProofToken` with `generateProofToken()` (same algorithm as backend
 *   `SignatureService.generateProofToken()`; see `docs/CRYPTO.md`), then signs it with the device key
 *   (EC P-256) via `cryptoService`.
 * - Verifies integration Ed25519 signatures on the pending response payload before displaying context.
 * - Surfaces meaningful errors to maintain the human-in-the-loop posture emphasised in `docs/features/AUTH_SECURITY.md`.
 * - Returns immediately to Enrollment Detail after a verified respond result, where the latest response summary is shown.
 *
 * @param route React Navigation route containing the target enrollment identifier.
 * @since 2025
 */
export const PendingAuthScreen: React.FC<Props> = ({route, navigation}) => {
  const {t} = useTranslation();
  const {enrollmentId, initialAttempt} = route.params;
  const {data: enrollment, isLoading: isEnrollmentLoading} = useEnrollmentById(enrollmentId);
  const markEnrollmentPendingChecked = useMarkEnrollmentPendingChecked();
  const setRecentAuthResult = useEnrollmentStore(store => store.setRecentAuthResult);
  const autoLoadEnrollmentRef = useRef<string | undefined>(undefined);
  const [attempt, setAttempt] = useState<PendingAttempt | undefined>(initialAttempt);
  const [challengeInput, setChallengeInput] = useState('');
  const [formError, setFormError] = useState<string | undefined>();
  const [globalError, setGlobalError] = useState<string | undefined>();
  const [isProcessing, setIsProcessing] = useState(false);
  const [loading, setLoading] = useState(false);
  /** On-screen debug info when an error occurs (no server/file needed). */
  const [debugInfo, setDebugInfo] = useState<{
    /** ISO 8601 timestamp when this debug snapshot started (confirms JS bundle / screen code version). */
    capturedAtIso?: string;
    lastStep: string;
    integrationPublicKeyLength?: number;
    integrationPublicKeyPrefix?: string;
    pendingPayloadLength?: number;
    /** SHA-256 hex of pendingPayload UTF-8; compare to Auth API log PENDING_PAYLOAD_DIAG payloadSha256Utf8Hex. */
    pendingPayloadSha256Utf8Hex?: string;
    pendingPayloadBase64?: string;
    pendingPayloadPreview?: string;
    signatureLength?: number;
    /** SHA-256 hex of integration signature Base64 string (UTF-8); compare PENDING_SIGNATURE_DIAG. */
    signatureSha256Utf8Hex?: string;
    signaturePrefix?: string;
    /** SHA-256 hex of integrationPublicKey string (UTF-8) passed to verify; compare PENDING_INTEGRATION_PUBLIC_KEY_DIAG. */
    integrationPublicKeySha256Utf8Hex?: string;
    signatureValid?: boolean;
    errorMessage?: string;
  } | undefined>(undefined);

  const extractErrorMessage = useCallback((error: unknown) => {
    if (axios.isAxiosError(error)) {
      const message =
        error.response?.data?.message ??
        error.response?.data?.error ??
        error.message ??
        t('pendingAuth.requestFailed');
      return message;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return t('pendingAuth.unexpectedError');
  }, [t]);

  const handleNoPendingResult = useCallback(() => {
    setAttempt(undefined);
    setChallengeInput('');
    setFormError(undefined);

    if (initialAttempt && navigation.canGoBack()) {
      navigation.goBack();
    }
  }, [initialAttempt, navigation]);

  const handleReturnToEnrollmentDetail = useCallback(() => {
    if (navigation.canGoBack()) {
      navigation.goBack();
      return;
    }

    navigation.navigate('EnrollmentDetail', {enrollmentId});
  }, [enrollmentId, navigation]);

  const loadPendingAttempt = useCallback(async () => {
    if (!enrollment) {
      return;
    }
    setLoading(true);
    setGlobalError(undefined);
    setDebugInfo(undefined);
    try {
      const debugSnapshotAt = new Date().toISOString();
      try {
        await markEnrollmentPendingChecked.mutateAsync({
          id: enrollment.id,
          checkedAt: debugSnapshotAt,
        });
      } catch (storageError) {
        console.warn('[PendingAuth] Failed to persist last verification timestamp:', storageError);
      }
      setDebugInfo({lastStep: 'start', capturedAtIso: debugSnapshotAt});
      const enrollmentKeyId = enrollment.id.toString();
      // Ensure EC P-256 key pair exists for this enrollment
      await cryptoService.ensureEnrollmentKeyPair(enrollmentKeyId);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_ensure'}
          : {lastStep: 'after_ensure', capturedAtIso: debugSnapshotAt},
      );
      const deviceProofToken = await generateProofToken();
      const deviceProofTokenSigned = await cryptoService.sign(enrollmentKeyId, deviceProofToken);
      const response = await authAttemptsApi.pending({
        enrollmentId: enrollment.id,
        enrollmentProofToken: enrollment.enrollmentProofToken,
        deviceProofToken,
        deviceProofTokenSigned,
      }, enrollment.installation?.authUrl);

      if (!response) {
        handleNoPendingResult();
        return;
      }
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_pending'}
          : {lastStep: 'after_pending', capturedAtIso: debugSnapshotAt},
      );

      // Verify integration signature over canonical payload (NFC + proofToken|challenge|title|message)
      const integrationPublicKey = enrollment.integrationPublicKey;
      if (!integrationPublicKey) {
        setGlobalError(t('pendingAuth.missingPendingPublicKey'));
        setAttempt(undefined);
        return;
      }
      const pendingPayload = buildPendingPayload(
        response.authAttemptProofToken,
        response.authAttemptChallengeRequired ?? false,
        response.contextTitle,
        response.contextMessage,
      );
      const pendingPayloadSha256Utf8Hex = sha256HexUtf8(pendingPayload);
      const payloadBase64 = Buffer.from(pendingPayload, 'utf8').toString('base64');
      const signature = response.authAttemptProofTokenSignedByIntegration ?? '';
      const signatureSha256Utf8Hex = sha256HexUtf8(signature);
      const integrationPublicKeySha256Utf8Hex = sha256HexUtf8(integrationPublicKey);
      setDebugInfo(prev =>
        prev
          ? {
              ...prev,
              lastStep: 'before_verify',
              integrationPublicKeyLength: integrationPublicKey?.length,
              integrationPublicKeyPrefix: integrationPublicKey?.slice(0, 24) ?? '',
              integrationPublicKeySha256Utf8Hex,
              pendingPayloadLength: pendingPayload.length,
              pendingPayloadSha256Utf8Hex,
              pendingPayloadBase64: payloadBase64,
              pendingPayloadPreview: pendingPayload.slice(0, 180),
              signatureLength: signature.length,
              signatureSha256Utf8Hex,
              signaturePrefix: signature.slice(0, 24),
            }
          : {
              lastStep: 'before_verify',
              capturedAtIso: debugSnapshotAt,
              integrationPublicKeyLength: integrationPublicKey?.length,
              integrationPublicKeyPrefix: integrationPublicKey?.slice(0, 24) ?? '',
              integrationPublicKeySha256Utf8Hex,
              pendingPayloadLength: pendingPayload.length,
              pendingPayloadSha256Utf8Hex,
              pendingPayloadBase64: payloadBase64,
              pendingPayloadPreview: pendingPayload.slice(0, 180),
              signatureLength: signature.length,
              signatureSha256Utf8Hex,
              signaturePrefix: signature.slice(0, 24),
            },
      );
      const signatureValid = await cryptoService.verify(
        pendingPayload,
        response.authAttemptProofTokenSignedByIntegration,
        integrationPublicKey,
      );
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_verify', signatureValid}
          : {lastStep: 'after_verify', signatureValid, capturedAtIso: debugSnapshotAt},
      );
      if (!signatureValid) {
        setGlobalError(t('pendingAuth.invalidPendingSignature'));
        setAttempt(undefined);
        return;
      }

      setAttempt({
        authAttemptId: String(response.authAttemptId),
        authAttemptProofToken: response.authAttemptProofToken,
        authAttemptProofTokenSignedByIntegration: response.authAttemptProofTokenSignedByIntegration,
        challengeRequired: response.authAttemptChallengeRequired,
        integrationName: enrollment.integrationName,
        tenantName: enrollment.tenantName,
        createdAt: new Date().toISOString(),
        contextTitle: response.contextTitle,
        contextMessage: response.contextMessage,
      });
      setChallengeInput('');
      setFormError(undefined);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_verify'}
          : {lastStep: 'after_verify', capturedAtIso: debugSnapshotAt},
      );
    } catch (error) {
      const msg = extractErrorMessage(error);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'catch', errorMessage: msg}
          : {lastStep: 'catch', errorMessage: msg, capturedAtIso: new Date().toISOString()},
      );
      setGlobalError(msg);
    } finally {
      setLoading(false);
    }
  }, [
    enrollment?.enrollmentProofToken,
    enrollment?.id,
    enrollment?.installation?.authUrl,
    enrollment?.integrationName,
    enrollment?.integrationPublicKey,
    enrollment?.tenantName,
    extractErrorMessage,
    handleNoPendingResult,
    markEnrollmentPendingChecked,
    t,
  ]);

  useEffect(() => {
    if (isEnrollmentLoading || !enrollment) {
      return;
    }
    if (initialAttempt) {
      autoLoadEnrollmentRef.current = enrollment.id;
      return;
    }
    if (autoLoadEnrollmentRef.current === enrollment.id) {
      return;
    }
    autoLoadEnrollmentRef.current = enrollment.id;
    loadPendingAttempt();
  }, [enrollment?.id, initialAttempt, isEnrollmentLoading, loadPendingAttempt]);

  const handleRespond = useCallback(
    async (accepted: boolean) => {
      if (!enrollment || !attempt || isProcessing) {
        return;
      }
      // With Ed25519, keys are derived on-demand, so we just need to ensure root key exists
      if (
        accepted &&
        attempt.challengeRequired &&
        challengeInput.trim().length !== AUTH_CHALLENGE_LENGTH
      ) {
        setFormError(t('pendingAuth.enterChallenge'));
        return;
      }
      setIsProcessing(true);
      setGlobalError(undefined);
      setFormError(undefined);
      try {
        // Ensure root key exists
        const enrollmentKeyId = enrollment.id.toString();
        await cryptoService.ensureEnrollmentKeyPair(enrollmentKeyId);
        const respondPayload = buildRespondPayload(attempt.authAttemptProofToken, accepted);
        const proofTokenSigned = await cryptoService.sign(enrollmentKeyId, respondPayload);
        const response = await authAttemptsApi.respond({
          authAttemptId: attempt.authAttemptId,
          authAttemptAccepted: accepted,
          authAttemptProofTokenSignedByDevice: proofTokenSigned,
          authAttemptChallengeResponse: challengeInput.trim() || undefined,
        }, enrollment.installation?.authUrl);

        const integrationPublicKey = enrollment.integrationPublicKey;
        if (!integrationPublicKey) {
          setGlobalError(t('pendingAuth.missingRespondPublicKey'));
          return;
        }
        const respondSig = response.authAttemptProofTokenResultSignedByIntegration?.trim() ?? '';
        if (!respondSig) {
          setGlobalError(t('pendingAuth.missingRespondSignature'));
          return;
        }
        const respondResultPayload = buildRespondResultPayload(
          attempt.authAttemptProofToken,
          response.authAttemptId,
          response.authAttemptResult,
          response.authAttemptMessage,
        );
        const respondSignatureValid = await cryptoService.verify(
          respondResultPayload,
          respondSig,
          integrationPublicKey,
        );
        if (!respondSignatureValid) {
          setGlobalError(t('pendingAuth.invalidRespondSignature'));
          return;
        }

        const outcome = response.authAttemptResult;
        const title = attempt.contextTitle ?? enrollment.integrationName;
        const message = response.authAttemptMessage ?? attempt.contextMessage;
        const status =
          outcome === 'APPROVED'
            ? 'approved'
            : outcome === 'DENIED'
              ? 'rejected'
              : 'failed';

        setRecentAuthResult(enrollment.id, {
          status,
          title,
          message: status === 'failed' ? message ?? t('pendingAuth.challengeDidNotMatch') : message,
          completedAt: new Date().toISOString(),
        });
        handleReturnToEnrollmentDetail();
      } catch (error) {
        setGlobalError(extractErrorMessage(error));
      } finally {
        setIsProcessing(false);
      }
    },
    [
      attempt,
      challengeInput,
      enrollment,
      extractErrorMessage,
      handleReturnToEnrollmentDetail,
      isProcessing,
      setRecentAuthResult,
      t,
    ],
  );

  const hasSecureInfo = true; // With Ed25519, keys are always available if root key exists
  const showEmptyState =
    !loading &&
    !attempt &&
    !globalError &&
    !isEnrollmentLoading &&
    hasSecureInfo;

  return (
    <View style={styles.container}>
      <Text style={styles.heading}>{t('pendingAuth.heading')}</Text>
      {enrollment && !attempt?.contextTitle ? (
        <View style={styles.enrollmentBox}>
          <Text style={styles.enrollmentIntegration}>{enrollment.integrationName}</Text>
          {enrollment.tenantName ? (
            <Text style={styles.enrollmentTenant}>{enrollment.tenantName}</Text>
          ) : null}
        </View>
      ) : null}
      {isEnrollmentLoading || loading ? (
        <View style={styles.loading}>
          <ActivityIndicator />
          <Text style={styles.loadingText}>{t('pendingAuth.loading')}</Text>
        </View>
      ) : globalError ? (
        <View style={styles.errorState}>
          <Text style={styles.errorTitle}>{t('pendingAuth.errorTitle')}</Text>
          <Text style={styles.errorBody}>{globalError}</Text>
          {env.pendingAuthDebugPanel && debugInfo ? (
            <View style={styles.debugBox}>
              <Text style={styles.debugTitle}>{t('pendingAuth.debugTitle')}</Text>
              {debugInfo.capturedAtIso != null && debugInfo.capturedAtIso !== '' && (
                <Text style={styles.debugTimestamp} selectable>
                  {t('pendingAuth.debugCapturedAt', {value: debugInfo.capturedAtIso})}
                </Text>
              )}
              <Text style={styles.debugLine}>{t('pendingAuth.debugLastStep', {value: debugInfo.lastStep})}</Text>
              {debugInfo.integrationPublicKeyLength != null && (
                <Text style={styles.debugLine}>{t('pendingAuth.debugIntegrationKeyLength', {value: debugInfo.integrationPublicKeyLength})}</Text>
              )}
              {debugInfo.integrationPublicKeyPrefix != null && (
                <Text style={styles.debugLine} selectable>{t('pendingAuth.debugIntegrationKeyPrefix', {value: debugInfo.integrationPublicKeyPrefix})}</Text>
              )}
              {debugInfo.integrationPublicKeySha256Utf8Hex != null &&
                debugInfo.integrationPublicKeySha256Utf8Hex !== '' && (
                  <Text style={styles.debugLine} selectable>
                    {t('pendingAuth.debugIntegrationKeySha', {value: debugInfo.integrationPublicKeySha256Utf8Hex})}
                  </Text>
                )}
              {debugInfo.pendingPayloadLength != null && (
                <Text style={styles.debugLine}>{t('pendingAuth.debugPendingPayloadLength', {value: debugInfo.pendingPayloadLength})}</Text>
              )}
              {debugInfo.pendingPayloadSha256Utf8Hex != null && debugInfo.pendingPayloadSha256Utf8Hex !== '' && (
                <Text style={styles.debugLine} selectable>
                  {t('pendingAuth.debugPendingPayloadSha', {value: debugInfo.pendingPayloadSha256Utf8Hex})}
                </Text>
              )}
              {debugInfo.pendingPayloadPreview != null && (
                <Text style={styles.debugLine} selectable>{t('pendingAuth.debugPendingPayloadPreview', {value: debugInfo.pendingPayloadPreview})}</Text>
              )}
              {debugInfo.pendingPayloadBase64 != null && (
                <Text style={styles.debugLine} selectable>{t('pendingAuth.debugPendingPayloadBase64', {value: debugInfo.pendingPayloadBase64})}</Text>
              )}
              {debugInfo.signatureLength != null && (
                <Text style={styles.debugLine}>{t('pendingAuth.debugSignatureLength', {value: debugInfo.signatureLength})}</Text>
              )}
              {debugInfo.signatureSha256Utf8Hex != null && debugInfo.signatureSha256Utf8Hex !== '' && (
                <Text style={styles.debugLine} selectable>
                  {t('pendingAuth.debugSignatureSha', {value: debugInfo.signatureSha256Utf8Hex})}
                </Text>
              )}
              {debugInfo.signaturePrefix != null && (
                <Text style={styles.debugLine} selectable>{t('pendingAuth.debugSignaturePrefix', {value: debugInfo.signaturePrefix})}</Text>
              )}
              {debugInfo.signatureValid != null && (
                <Text style={styles.debugLine}>{t('pendingAuth.debugSignatureValid', {value: String(debugInfo.signatureValid)})}</Text>
              )}
              {debugInfo.errorMessage != null && (
                <Text style={styles.debugLine} selectable>{t('pendingAuth.debugError', {value: debugInfo.errorMessage})}</Text>
              )}
            </View>
          ) : null}
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.secondaryButton}>
            <Text style={styles.secondaryLabel}>{t('pendingAuth.tryAgain')}</Text>
          </TouchableOpacity>
        </View>
      ) : showEmptyState ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>{t('pendingAuth.noPending')}</Text>
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.checkAgainButton}>
            <Text style={styles.checkAgainLabel}>{t('pendingAuth.checkAgain')}</Text>
          </TouchableOpacity>
        </View>
      ) : (
        attempt && (
          <ScrollView
            showsVerticalScrollIndicator={false}
            contentContainerStyle={styles.pendingScrollContent}>
            <View style={styles.card}>
              {/* Card header: context title (when present) or integration name + Pending */}
              <View style={styles.cardHeader}>
                <Text style={styles.cardTitle}>
                  {attempt.contextTitle ?? attempt.integrationName}
                  <Text style={styles.cardTitlePending}> {t('pendingAuth.pendingSuffix')}</Text>
                </Text>
              </View>

              {/* Subtitle: tenant name only when no context (context card is self-contained) */}
              {!attempt.contextTitle ? (
                attempt.tenantName ? (
                  <Text style={styles.cardSubtitle}>{attempt.tenantName}</Text>
                ) : null
              ) : null}

              {/* Context message */}
              {attempt.contextMessage ? (
                <View style={[styles.contextMessageBox, styles.borderInfo]}>
                  <Text style={styles.contextMessageText}>{attempt.contextMessage}</Text>
                </View>
              ) : null}

              {attempt.challengeRequired ? (
                <View style={styles.challengeSection}>
                  <Text style={styles.challengeHeading}>{t('pendingAuth.challengeHeading')}</Text>
                  <AuthChallengeCodeInput
                    value={challengeInput}
                    onChangeText={setChallengeInput}
                    onClearError={() => setFormError(undefined)}
                    editable={!isProcessing}
                  />
                  {formError ? (
                    <View style={styles.errorBanner}>
                      <Text style={styles.errorBannerText}>{formError}</Text>
                    </View>
                  ) : null}
                </View>
              ) : null}

              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={() => handleRespond(false)}
                  style={[styles.actionButton, styles.rejectButton]}
                  disabled={isProcessing}>
                  <Text style={styles.rejectLabel}>{t('pendingAuth.deny')}</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => handleRespond(true)}
                  style={[styles.actionButton, styles.approveButton]}
                  disabled={isProcessing}>
                  <Text style={styles.approveLabel}>
                    {isProcessing ? t('pendingAuth.sending') : t('pendingAuth.approve')}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          </ScrollView>
        )
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 16,
    backgroundColor: '#0b0d11',
  },
  heading: {
    fontSize: 24,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  pendingScrollContent: {
    paddingBottom: 24,
    flexGrow: 1,
  },
  enrollmentBox: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.15)',
  },
  enrollmentIntegration: {
    fontSize: 16,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  enrollmentTenant: {
    fontSize: 14,
    color: '#9aa3b6',
    marginTop: 4,
  },
  loading: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingText: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#f4f7ff',
    textAlign: 'center',
  },
  checkAgainButton: {
    backgroundColor: '#3076df',
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 24,
    marginTop: 12,
  },
  checkAgainLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#ffffff',
  },
  card: {
    backgroundColor: '#151923',
    borderRadius: 16,
    padding: 20,
    gap: 16,
  },
  cardHeader: {},
  cardTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  cardTitlePending: {
    color: '#61d095',
    fontWeight: '700',
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#c2c8d5',
  },
  challengeSection: {
    gap: 12,
  },
  challengeHeading: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
    lineHeight: 24,
  },
  challengeContainer: {
    position: 'relative',
  },
  challengeBoxes: {
    flexDirection: 'row',
    gap: 12,
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
  errorBanner: {
    backgroundColor: 'rgba(255, 120, 120, 0.15)',
    borderLeftWidth: 4,
    borderLeftColor: '#ff6666',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  errorBannerText: {
    fontSize: 14,
    color: '#ff6666',
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
  },
  actionButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  rejectButton: {
    borderWidth: 1,
    borderColor: '#ff7878',
  },
  approveButton: {
    backgroundColor: '#61d095',
  },
  rejectLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#ff7878',
  },
  approveLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#0b0d11',
  },
  secondaryButton: {
    alignItems: 'center',
    lineHeight: 18,
  },
  secondaryLabel: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  /* Context level badge variants */
  badgeInfo: {
    color: '#61d095',
  },
  badgeWarning: {
    color: '#f5a623',
  },
  badgeCritical: {
    color: '#ff6666',
  },
  /* Context message box */
  contextMessageBox: {
    borderLeftWidth: 3,
    paddingLeft: 12,
    paddingVertical: 8,
    backgroundColor: '#1c2130',
    borderRadius: 8,
  },
  contextMessageText: {
    fontSize: 14,
    color: '#e0e5f0',
    lineHeight: 20,
  },
  /* Context level border accents */
  borderInfo: {
    borderLeftColor: '#61d095',
  },
  borderWarning: {
    borderLeftColor: '#f5a623',
  },
  borderCritical: {
    borderLeftColor: '#ff6666',
  },
  errorState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  errorBody: {
    fontSize: 14,
    color: '#ff6666',
    textAlign: 'center',
  },
  debugBox: {
    alignSelf: 'stretch',
    backgroundColor: '#1a1d26',
    borderRadius: 8,
    padding: 12,
    marginTop: 8,
    borderWidth: 1,
    borderColor: 'rgba(154, 163, 182, 0.3)',
  },
  debugTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: '#9aa3b6',
    marginBottom: 4,
  },
  debugTimestamp: {
    fontSize: 11,
    color: '#61d095',
    fontFamily: 'monospace',
    marginBottom: 8,
  },
  debugLine: {
    fontSize: 11,
    color: '#9aa3b6',
    fontFamily: 'monospace',
    marginTop: 2,
  },
});
