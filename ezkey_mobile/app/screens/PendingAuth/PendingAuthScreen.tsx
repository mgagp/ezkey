/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: PendingAuthScreen
 * Description: React Native screen orchestrating the device-side pending and respond flows.
 * Security Context: Embeds the polling model, proof token usage, and read-once semantics described in
 *                   docs/features/AUTH_SECURITY.md to keep user actions intentional and replay resistant.
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
import {env} from '../../config/env';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentById} from '../../hooks/useEnrollments';
import {authAttemptsApi} from '../../services/api/authAttempts';
import {
  buildPendingPayload,
  buildRespondPayload,
  buildRespondResultPayload,
} from '../../services/crypto/authAttemptPayload';
import {cryptoService} from '../../services/crypto';
import {sha256HexUtf8} from '../../utils/sha256HexUtf8';
import {RespondMitmLabControl} from '../../components/RespondMitmLabControl';

type Props = NativeStackScreenProps<RootStackParamList, 'PendingAuth'>;

type AttemptState = 'pending' | 'accepted' | 'rejected' | 'expired' | 'failed';

type PendingAttempt = {
  authAttemptId: string;
  authAttemptProofToken: string;
  authAttemptProofTokenSignedByIntegration: string;
  integrationName: string;
  tenantName: string;
  createdAt: string;
  challengeRequired: boolean;
  contextTitle?: string;
  contextMessage?: string;
};

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
      accessibilityLabel="Challenge code input"
      accessibilityHint="Enter the 2-digit code shown in the admin console">
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
 * Presents pending authentication attempts for a selected enrollment and enables the user to accept or deny them.
 *
 * - Generates device proof tokens per poll, mirroring the guidance in `docs/CRYPTO.md`.
 * - Submits Ed25519 signatures through `cryptoService` to guarantee parity with the backend `SignatureService`.
 * - Surfaces meaningful errors to maintain the human-in-the-loop posture emphasised in `docs/features/AUTH_SECURITY.md`.
 *
 * @param route React Navigation route containing the target enrollment identifier.
 * @since 2025
 */
export const PendingAuthScreen: React.FC<Props> = ({route}) => {
  const {enrollmentId} = route.params;
  const {data: enrollment, isLoading: isEnrollmentLoading} = useEnrollmentById(enrollmentId);
  const [attempt, setAttempt] = useState<PendingAttempt | undefined>();
  const [state, setState] = useState<AttemptState>('pending');
  const [challengeInput, setChallengeInput] = useState('');
  const [formError, setFormError] = useState<string | undefined>();
  const [globalError, setGlobalError] = useState<string | undefined>();
  const [challengeFailedMessage, setChallengeFailedMessage] = useState<string | undefined>();
  const [isProcessing, setIsProcessing] = useState(false);
  const [loading, setLoading] = useState(false);
  /** Lab: send authAttemptAccepted opposite to signed payload (invalid device signature on server). */
  const [simulateRespondMitmMismatch, setSimulateRespondMitmMismatch] = useState(false);
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
        'Request failed.';
      return message;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'Unexpected error.';
  }, []);

  const loadPendingAttempt = useCallback(async () => {
    if (!enrollment) {
      return;
    }
    // With Ed25519, keys are derived on-demand, so we just need to ensure root key exists
    setLoading(true);
    setGlobalError(undefined);
    setChallengeFailedMessage(undefined);
    setDebugInfo(undefined);
    try {
      const debugSnapshotAt = new Date().toISOString();
      setDebugInfo({lastStep: 'start', capturedAtIso: debugSnapshotAt});
      const enrollmentId = enrollment.id.toString();
      // Ensure EC P-256 key pair exists for this enrollment
      await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_ensure'}
          : {lastStep: 'after_ensure', capturedAtIso: debugSnapshotAt},
      );
      const deviceProofToken = Date.now().toString();
      const deviceProofTokenSigned = await cryptoService.sign(enrollmentId, deviceProofToken);
      const response = await authAttemptsApi.pending({
        enrollmentId: enrollment.id,
        enrollmentProofToken: enrollment.enrollmentProofToken,
        deviceProofToken,
        deviceProofTokenSigned,
      }, enrollment.authUrl);

      if (!response) {
        setAttempt(undefined);
        setState('pending');
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
        setGlobalError('Enrollment missing integration public key; cannot verify pending response.');
        setAttempt(undefined);
        setState('pending');
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
        setGlobalError('Invalid integration signature on pending response.');
        setAttempt(undefined);
        setState('pending');
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
      setState('pending');
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
  }, [enrollment, extractErrorMessage]);

  useEffect(() => {
    if (isEnrollmentLoading || !enrollment) {
      return;
    }
    loadPendingAttempt();
  }, [enrollment, isEnrollmentLoading, loadPendingAttempt]);

  const handleRespond = useCallback(
    async (accepted: boolean) => {
      if (!enrollment || !attempt || state !== 'pending') {
        return;
      }
      // With Ed25519, keys are derived on-demand, so we just need to ensure root key exists
      if (
        accepted &&
        attempt.challengeRequired &&
        challengeInput.trim().length !== AUTH_CHALLENGE_LENGTH
      ) {
        setFormError('Enter the 2-digit code from the admin console.');
        return;
      }
      setIsProcessing(true);
      setGlobalError(undefined);
      setFormError(undefined);
      try {
        // Ensure root key exists
        const enrollmentId = enrollment.id.toString();
        await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
        /** User's real decision — always what we sign (proofToken|accepted). */
        const signAccepted = accepted;
        /** Wire value: MITM sim sends the opposite flag so JSON ≠ signed payload. */
        const wireAccepted =
          env.labRespondMitmSimulator && simulateRespondMitmMismatch ? !accepted : accepted;
        const respondPayload = buildRespondPayload(attempt.authAttemptProofToken, signAccepted);
        const proofTokenSigned = await cryptoService.sign(enrollmentId, respondPayload);
        const response = await authAttemptsApi.respond({
          authAttemptId: attempt.authAttemptId,
          authAttemptAccepted: wireAccepted,
          authAttemptProofTokenSignedByDevice: proofTokenSigned,
          authAttemptChallengeResponse: challengeInput.trim() || undefined,
        }, enrollment.authUrl);

        const integrationPublicKey = enrollment.integrationPublicKey;
        if (!integrationPublicKey) {
          setGlobalError('Enrollment missing integration public key; cannot verify respond response.');
          return;
        }
        const respondSig = response.authAttemptProofTokenResultSignedByIntegration?.trim() ?? '';
        if (!respondSig) {
          setGlobalError('Missing integration signature on respond response.');
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
          setGlobalError('Invalid integration signature on respond response.');
          return;
        }

        const outcome = response.authAttemptResult;
        if (outcome === 'APPROVED') {
          setState('accepted');
        } else if (outcome === 'DENIED') {
          setState('rejected');
        } else {
          setAttempt(undefined);
          setChallengeInput('');
          setState('failed');
          setChallengeFailedMessage(
            response.authAttemptMessage ??
              'Challenge code did not match. This attempt is final.',
          );
        }
      } catch (error) {
        setGlobalError(extractErrorMessage(error));
      } finally {
        setIsProcessing(false);
      }
    },
    [attempt, challengeInput, enrollment, extractErrorMessage, simulateRespondMitmMismatch, state],
  );

  const hasSecureInfo = true; // With Ed25519, keys are always available if root key exists
  const showChallengeFailed = state === 'failed' && !!challengeFailedMessage;
  const showEmptyState =
    !loading &&
    !attempt &&
    !globalError &&
    !challengeFailedMessage &&
    !isEnrollmentLoading &&
    hasSecureInfo;

  const showResultState = (state === 'accepted' || state === 'rejected') && enrollment;

  return (
    <View style={styles.container}>
      <Text style={styles.heading}>Pending authentication</Text>
      {enrollment && !showResultState && !attempt?.contextTitle ? (
        <View style={styles.enrollmentBox}>
          <Text style={styles.enrollmentIntegration}>{enrollment.integrationName}</Text>
          <Text style={styles.enrollmentTenant}>{enrollment.tenantName}</Text>
        </View>
      ) : null}
      {isEnrollmentLoading || loading ? (
        <View style={styles.loading}>
          <ActivityIndicator />
          <Text style={styles.loadingText}>Contacting Ezkey Auth API…</Text>
        </View>
      ) : globalError ? (
        <View style={styles.errorState}>
          <Text style={styles.errorTitle}>Unable to load request</Text>
          <Text style={styles.errorBody}>{globalError}</Text>
          {env.pendingAuthDebugPanel && debugInfo ? (
            <View style={styles.debugBox}>
              <Text style={styles.debugTitle}>Debug (for support)</Text>
              {debugInfo.capturedAtIso != null && debugInfo.capturedAtIso !== '' && (
                <Text style={styles.debugTimestamp} selectable>
                  Captured at: {debugInfo.capturedAtIso}
                </Text>
              )}
              <Text style={styles.debugLine}>Last step: {debugInfo.lastStep}</Text>
              {debugInfo.integrationPublicKeyLength != null && (
                <Text style={styles.debugLine}>integrationPublicKey length: {debugInfo.integrationPublicKeyLength}</Text>
              )}
              {debugInfo.integrationPublicKeyPrefix != null && (
                <Text style={styles.debugLine} selectable>integrationPublicKey prefix: {debugInfo.integrationPublicKeyPrefix}</Text>
              )}
              {debugInfo.integrationPublicKeySha256Utf8Hex != null &&
                debugInfo.integrationPublicKeySha256Utf8Hex !== '' && (
                  <Text style={styles.debugLine} selectable>
                    integrationPublicKey SHA256 (UTF-8 hex): {debugInfo.integrationPublicKeySha256Utf8Hex}
                  </Text>
                )}
              {debugInfo.pendingPayloadLength != null && (
                <Text style={styles.debugLine}>pendingPayload length: {debugInfo.pendingPayloadLength}</Text>
              )}
              {debugInfo.pendingPayloadSha256Utf8Hex != null && debugInfo.pendingPayloadSha256Utf8Hex !== '' && (
                <Text style={styles.debugLine} selectable>
                  pendingPayload SHA256 (UTF-8 hex): {debugInfo.pendingPayloadSha256Utf8Hex}
                </Text>
              )}
              {debugInfo.pendingPayloadPreview != null && (
                <Text style={styles.debugLine} selectable>pendingPayload preview: {debugInfo.pendingPayloadPreview}</Text>
              )}
              {debugInfo.pendingPayloadBase64 != null && (
                <Text style={styles.debugLine} selectable>pendingPayload base64: {debugInfo.pendingPayloadBase64}</Text>
              )}
              {debugInfo.signatureLength != null && (
                <Text style={styles.debugLine}>signature length: {debugInfo.signatureLength}</Text>
              )}
              {debugInfo.signatureSha256Utf8Hex != null && debugInfo.signatureSha256Utf8Hex !== '' && (
                <Text style={styles.debugLine} selectable>
                  integration signature SHA256 (UTF-8 hex): {debugInfo.signatureSha256Utf8Hex}
                </Text>
              )}
              {debugInfo.signaturePrefix != null && (
                <Text style={styles.debugLine} selectable>signature prefix: {debugInfo.signaturePrefix}</Text>
              )}
              {debugInfo.signatureValid != null && (
                <Text style={styles.debugLine}>signature valid: {String(debugInfo.signatureValid)}</Text>
              )}
              {debugInfo.errorMessage != null && (
                <Text style={styles.debugLine} selectable>Error: {debugInfo.errorMessage}</Text>
              )}
            </View>
          ) : null}
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.secondaryButton}>
            <Text style={styles.secondaryLabel}>Try again</Text>
          </TouchableOpacity>
        </View>
      ) : showChallengeFailed ? (
        <View style={styles.challengeFailedState}>
          <Text style={styles.challengeFailedTitle}>Authentication failed</Text>
          <Text style={styles.challengeFailedBody}>{challengeFailedMessage}</Text>
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.checkAgainButton}>
            <Text style={styles.checkAgainLabel}>Check again</Text>
          </TouchableOpacity>
        </View>
      ) : showEmptyState ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>No pending requests</Text>
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.checkAgainButton}>
            <Text style={styles.checkAgainLabel}>Check again</Text>
          </TouchableOpacity>
        </View>
      ) : showResultState ? (
        <View style={styles.resultContainer}>
          <View style={styles.identityZone}>
            <Text style={styles.resultTitleLine}>
              {attempt?.contextTitle ?? enrollment.integrationName}
              <Text
                style={[
                  styles.resultStatusSuffix,
                  state === 'accepted' ? styles.badgeApproved : styles.badgeRejected,
                ]}>
                {' '}
                {state === 'accepted' ? 'Approved' : 'Rejected'}
              </Text>
            </Text>
            {attempt?.contextMessage ? (
              <Text style={styles.contextMessageLine}>{attempt.contextMessage}</Text>
            ) : (
              <>
                <Text style={styles.tenantLine}>{enrollment.tenantName}</Text>
                {enrollment.enrollmentName ? (
                  <Text style={styles.deviceLine}>{enrollment.enrollmentName}</Text>
                ) : null}
              </>
            )}
          </View>
          <View style={styles.metaZone}>
            <Text style={styles.metaLine}>
              Created {new Date(enrollment.createdAt).toLocaleString(undefined, {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}{' '}
              · Last {new Date(enrollment.lastActivityAt).toLocaleString(undefined, {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}
            </Text>
          </View>
          <TouchableOpacity
            onPress={loadPendingAttempt}
            style={[styles.checkAgainButton, styles.checkAgainButtonFull]}>
            <Text style={styles.checkAgainLabel}>Check again</Text>
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
                  <Text style={styles.cardTitlePending}> Pending</Text>
                </Text>
              </View>

              {/* Subtitle: tenant name only when no context (context card is self-contained) */}
              {!attempt.contextTitle ? (
                <Text style={styles.cardSubtitle}>{attempt.tenantName}</Text>
              ) : null}

              {/* Context message */}
              {attempt.contextMessage ? (
                <View style={[styles.contextMessageBox, styles.borderInfo]}>
                  <Text style={styles.contextMessageText}>{attempt.contextMessage}</Text>
                </View>
              ) : null}

              {attempt.challengeRequired ? (
                <View style={styles.challengeSection}>
                  <Text style={styles.challengeHeading}>Enter the 2-digit code from the admin console</Text>
                  <AuthChallengeCodeInput
                    value={challengeInput}
                    onChangeText={setChallengeInput}
                    onClearError={() => setFormError(undefined)}
                    editable={state === 'pending' && !isProcessing}
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
                  <Text style={styles.rejectLabel}>Deny</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => handleRespond(true)}
                  style={[styles.actionButton, styles.approveButton]}
                  disabled={isProcessing}>
                  <Text style={styles.approveLabel}>
                    {isProcessing ? 'Sending…' : 'Approve'}
                  </Text>
                </TouchableOpacity>
              </View>
              {env.labRespondMitmSimulator ? (
                <RespondMitmLabControl
                  enabled={simulateRespondMitmMismatch}
                  onEnabledChange={setSimulateRespondMitmMismatch}
                />
              ) : null}
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
  challengeFailedState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
    paddingHorizontal: 24,
  },
  challengeFailedTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#ff7878',
    textAlign: 'center',
  },
  challengeFailedBody: {
    fontSize: 15,
    color: '#9aa3b6',
    textAlign: 'center',
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
  checkAgainButtonFull: {
    alignSelf: 'stretch',
    alignItems: 'center',
    paddingVertical: 16,
    marginTop: 0,
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
  badge: {
    fontSize: 12,
    fontWeight: '700',
    color: '#61d095',
    flexShrink: 0,
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#c2c8d5',
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  metaLabel: {
    fontSize: 12,
    color: '#9aa3b6',
    letterSpacing: 0.4,
    textTransform: 'uppercase',
  },
  metaValue: {
    fontSize: 14,
    color: '#f4f7ff',
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
  resultContainer: {
    flex: 1,
    paddingTop: 8,
    gap: 16,
  },
  identityZone: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.15)',
  },
  identityRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  integrationName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  resultTitleLine: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  resultStatusSuffix: {
    fontWeight: '700',
  },
  statusBadge: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  badgeApproved: {
    color: '#61d095',
  },
  badgeRejected: {
    color: '#ff7878',
  },
  tenantLine: {
    fontSize: 14,
    color: '#9aa3b6',
    marginTop: 6,
  },
  contextMessageLine: {
    fontSize: 14,
    color: '#c2c8d5',
    marginTop: 6,
    lineHeight: 20,
  },
  deviceLine: {
    fontSize: 13,
    color: '#c2c8d5',
    marginTop: 2,
  },
  metaZone: {
    paddingHorizontal: 4,
  },
  metaLine: {
    fontSize: 12,
    color: '#9aa3b6',
  },
  secondaryButton: {
    alignItems: 'center',
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
