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

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
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
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentById} from '../../hooks/useEnrollments';
import {authAttemptsApi} from '../../services/api/authAttempts';
import {cryptoService} from '../../services/crypto';

type Props = NativeStackScreenProps<RootStackParamList, 'PendingAuth'>;

type AttemptState = 'pending' | 'accepted' | 'rejected' | 'expired';

type PendingAttempt = {
  authAttemptId: string;
  authAttemptProofToken: string;
  authAttemptProofTokenSignedByIntegration: string;
  integrationName: string;
  tenantName: string;
  createdAt: string;
  challengeRequired: boolean;
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
  const [isProcessing, setIsProcessing] = useState(false);
  const [loading, setLoading] = useState(false);

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
    try {
      const enrollmentId = enrollment.id.toString();
      // Ensure EC P-256 key pair exists for this enrollment
      await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
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

      setAttempt({
        authAttemptId: response.authAttemptId,
        authAttemptProofToken: response.authAttemptProofToken,
        authAttemptProofTokenSignedByIntegration: response.authAttemptProofTokenSignedByIntegration,
        challengeRequired: response.authAttemptChallengeRequired,
        integrationName: enrollment.integrationName,
        tenantName: enrollment.tenantName,
        createdAt: new Date().toISOString(),
      });
      setChallengeInput('');
      setFormError(undefined);
      setState('pending');
      } catch (error) {
        setGlobalError(extractErrorMessage(error));
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

  const formattedWindow = useMemo(() => {
    if (!attempt) {
      return undefined;
    }
    const created = new Date(attempt.createdAt).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
    });
    return `${created} → ongoing`;
  }, [attempt]);

  const handleRespond = useCallback(
    async (accepted: boolean) => {
      if (!enrollment || !attempt || state !== 'pending') {
        return;
      }
      // With Ed25519, keys are derived on-demand, so we just need to ensure root key exists
      if (attempt.challengeRequired && !challengeInput.trim()) {
        setFormError('Challenge code is required.');
        return;
      }
      setIsProcessing(true);
      setGlobalError(undefined);
      setFormError(undefined);
      try {
        // Ensure root key exists
        const enrollmentId = enrollment.id.toString();
        await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
        const proofTokenSigned = await cryptoService.sign(
          enrollmentId,
          attempt.authAttemptProofToken,
        );
        await authAttemptsApi.respond({
          authAttemptId: attempt.authAttemptId,
          authAttemptAccepted: accepted,
          authAttemptProofTokenSignedByDevice: proofTokenSigned,
          authAttemptChallengeResponse: challengeInput.trim() || undefined,
        }, enrollment.authUrl);
        setState(accepted ? 'accepted' : 'rejected');
        Alert.alert(
          accepted ? 'Authentication approved' : 'Authentication rejected',
          accepted ? 'Response submitted successfully.' : 'The request was denied.',
        );
      } catch (error) {
        setGlobalError(extractErrorMessage(error));
      } finally {
        setIsProcessing(false);
      }
    },
    [attempt, challengeInput, enrollment, extractErrorMessage, state],
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
      <Text style={styles.heading}>Pending authentication</Text>
      <Text style={styles.subtitle}>
        Enrollment ID <Text style={styles.emphasis}>{enrollmentId}</Text>
      </Text>
      {isEnrollmentLoading || loading ? (
        <View style={styles.loading}>
          <ActivityIndicator />
          <Text style={styles.loadingText}>Contacting Ezkey Auth API…</Text>
        </View>
      ) : globalError ? (
        <View style={styles.errorState}>
          <Text style={styles.errorTitle}>Unable to load request</Text>
          <Text style={styles.errorBody}>{globalError}</Text>
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.secondaryButton}>
            <Text style={styles.secondaryLabel}>Try again</Text>
          </TouchableOpacity>
        </View>
      ) : showEmptyState ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>No pending requests</Text>
          <Text style={styles.emptyBody}>
            Pull to refresh or wait for a new authentication attempt to arrive.
          </Text>
          <TouchableOpacity onPress={loadPendingAttempt} style={styles.secondaryButton}>
            <Text style={styles.secondaryLabel}>Check again</Text>
          </TouchableOpacity>
        </View>
      ) : (
        attempt && (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>{attempt.integrationName}</Text>
              <Text style={styles.badge}>PENDING</Text>
            </View>
            <Text style={styles.cardSubtitle}>{attempt.tenantName}</Text>
            <View style={styles.metaRow}>
              <Text style={styles.metaLabel}>Auth attempt ID</Text>
              <Text style={styles.metaValue}>{attempt.authAttemptId}</Text>
            </View>
            {formattedWindow ? (
              <View style={styles.metaRow}>
                <Text style={styles.metaLabel}>Response window</Text>
                <Text style={styles.metaValue}>{formattedWindow}</Text>
              </View>
            ) : null}
            {attempt.challengeRequired ? (
              <View style={styles.challengeSection}>
                <Text style={styles.challengeLabel}>Challenge code</Text>
                <Text style={styles.challengeHint}>Enter the two-digit code displayed in Console.</Text>
                <TextInput
                  value={challengeInput}
                  onChangeText={text => {
                    setChallengeInput(text.replace(/[^0-9]/g, '').slice(0, 2));
                    setFormError(undefined);
                  }}
                  placeholder="00"
                  keyboardType="number-pad"
                  maxLength={2}
                  style={styles.challengeInput}
                  placeholderTextColor="#5f6780"
                  editable={state === 'pending' && !isProcessing}
                />
                {formError ? <Text style={styles.formError}>{formError}</Text> : null}
              </View>
            ) : null}
            {state === 'pending' ? (
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
            ) : (
              <View style={styles.decisionBanner}>
                <Text style={styles.decisionText}>
                  {state === 'accepted'
                    ? 'This authentication was approved.'
                    : 'This authentication was rejected.'}
                </Text>
                <TouchableOpacity onPress={loadPendingAttempt} style={styles.secondaryButton}>
                  <Text style={styles.secondaryLabel}>Await new request</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
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
  subtitle: {
    fontSize: 16,
    color: '#c2c8d5',
  },
  emphasis: {
    fontWeight: '600',
    color: '#f4f7ff',
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
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  emptyBody: {
    fontSize: 14,
    color: '#9aa3b6',
    textAlign: 'center',
  },
  card: {
    backgroundColor: '#151923',
    borderRadius: 16,
    padding: 20,
    gap: 16,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  badge: {
    fontSize: 12,
    fontWeight: '700',
    color: '#61d095',
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
    gap: 8,
  },
  challengeLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  challengeHint: {
    fontSize: 13,
    color: '#9aa3b6',
  },
  challengeInput: {
    backgroundColor: '#0b0d11',
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 16,
    color: '#f4f7ff',
    fontSize: 16,
  },
  formError: {
    fontSize: 12,
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
  decisionBanner: {
    gap: 12,
  },
  decisionText: {
    fontSize: 15,
    color: '#9aa3b6',
  },
  secondaryButton: {
    alignItems: 'center',
  },
  secondaryLabel: {
    fontSize: 14,
    color: '#9aa3b6',
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
});
