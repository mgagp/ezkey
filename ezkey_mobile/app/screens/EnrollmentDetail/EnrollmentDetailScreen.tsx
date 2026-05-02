/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentDetailScreen
 * Description: Displays enrollment metadata and routes to pending auth. Optimized for the primary action: Check pending.
 * Delete enrollment moved to Danger Zone (Manage screen).
 * @since 2025
 */

import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {ActivityIndicator, Button, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import axios from 'axios';
import {useTranslation} from 'react-i18next';
import {useEnrollments, useMarkEnrollmentPendingChecked} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {authAttemptsApi} from '../../services/api/authAttempts';
import {buildPendingPayload} from '../../services/crypto/authAttemptPayload';
import {cryptoService} from '../../services/crypto';
import {generateProofToken} from '../../utils/generateProofToken';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

/**
 * Screen that surfaces enrollment metadata and routes to pending auth.
 * Primary action: Check pending. Delete moved to Danger Zone.
 *
 * @since 2025
 */
export const EnrollmentDetailScreen: React.FC<Props> = ({route, navigation}) => {
  const {t} = useTranslation();
  const {enrollmentId, autoCheckPendingNonce} = route.params;
  const {data, isLoading} = useEnrollments();
  const markEnrollmentPendingChecked = useMarkEnrollmentPendingChecked();
  const selectedId = useEnrollmentStore(store => store.selectedId);
  const targetId = enrollmentId ?? selectedId;
  const [isCheckingPending, setIsCheckingPending] = useState(false);
  const [inlineFeedback, setInlineFeedback] = useState<string | undefined>();
  const lastAutoCheckNonceRef = useRef<string | undefined>(undefined);

  const enrollment = useMemo(() => {
    if (!targetId || !data) {
      return undefined;
    }
    return data.find(item => item.id === targetId);
  }, [data, targetId]);

  useEffect(() => {
    if (enrollment) {
      navigation.setOptions({title: enrollment.integrationName});
    }
  }, [enrollment, navigation]);

  const extractErrorMessage = useCallback(
    (error: unknown) => {
      if (axios.isAxiosError(error)) {
        return (
          error.response?.data?.message ??
          error.response?.data?.error ??
          error.message ??
          t('pendingAuth.requestFailed')
        );
      }
      if (error instanceof Error) {
        return error.message;
      }
      return t('pendingAuth.unexpectedError');
    },
    [t],
  );

  const handleCheckPending = useCallback(async () => {
    if (!enrollment || isCheckingPending) {
      return;
    }

    setInlineFeedback(undefined);
    setIsCheckingPending(true);
    try {
      const checkedAt = new Date().toISOString();
      try {
        await markEnrollmentPendingChecked.mutateAsync({
          id: enrollment.id,
          checkedAt,
        });
      } catch (storageError) {
        console.warn('[EnrollmentDetail] Failed to persist last verification timestamp:', storageError);
      }

      const enrollmentKeyId = enrollment.id.toString();
      await cryptoService.ensureEnrollmentKeyPair(enrollmentKeyId);
      const deviceProofToken = await generateProofToken();
      const deviceProofTokenSigned = await cryptoService.sign(enrollmentKeyId, deviceProofToken);
      const response = await authAttemptsApi.pending(
        {
          enrollmentId: enrollment.id,
          enrollmentProofToken: enrollment.enrollmentProofToken,
          deviceProofToken,
          deviceProofTokenSigned,
        },
        enrollment.installation?.authUrl,
      );

      if (!response) {
        setInlineFeedback(t('pendingAuth.noPending'));
        return;
      }

      const integrationPublicKey = enrollment.integrationPublicKey;
      if (!integrationPublicKey) {
        setInlineFeedback(t('pendingAuth.missingPendingPublicKey'));
        return;
      }

      const pendingPayload = buildPendingPayload(
        response.authAttemptProofToken,
        response.authAttemptChallengeRequired ?? false,
        response.contextTitle,
        response.contextMessage,
      );
      const signatureValid = await cryptoService.verify(
        pendingPayload,
        response.authAttemptProofTokenSignedByIntegration,
        integrationPublicKey,
      );
      if (!signatureValid) {
        setInlineFeedback(t('pendingAuth.invalidPendingSignature'));
        return;
      }

      navigation.navigate('PendingAuth', {
        enrollmentId,
        initialAttempt: {
          authAttemptId: String(response.authAttemptId),
          authAttemptProofToken: response.authAttemptProofToken,
          authAttemptProofTokenSignedByIntegration: response.authAttemptProofTokenSignedByIntegration,
          challengeRequired: response.authAttemptChallengeRequired,
          integrationName: enrollment.integrationName,
          tenantName: enrollment.tenantName,
          createdAt: checkedAt,
          contextTitle: response.contextTitle,
          contextMessage: response.contextMessage,
        },
      });
    } catch (error) {
      setInlineFeedback(extractErrorMessage(error));
    } finally {
      setIsCheckingPending(false);
    }
  }, [
    enrollment,
    enrollmentId,
    extractErrorMessage,
    isCheckingPending,
    markEnrollmentPendingChecked,
    navigation,
    t,
  ]);

  useEffect(() => {
    if (!autoCheckPendingNonce || !enrollment || isCheckingPending) {
      return;
    }
    if (lastAutoCheckNonceRef.current === autoCheckPendingNonce) {
      return;
    }

    lastAutoCheckNonceRef.current = autoCheckPendingNonce;
    handleCheckPending();
  }, [autoCheckPendingNonce, enrollment, handleCheckPending, isCheckingPending]);

  if (isLoading) {
    return (
      <View style={styles.loadingContainer} accessibilityLabel={t('enrollmentDetail.loading')}>
        <ActivityIndicator accessibilityLabel={t('common.loading')} />
      </View>
    );
  }

  if (!enrollment) {
    return (
      <View style={styles.missingContainer}>
        <Text style={styles.missingText}>{t('enrollmentDetail.missing')}</Text>
        <Button title={t('enrollmentDetail.backToHome')} onPress={() => navigation.popToTop()} />
      </View>
    );
  }

  const createdStr = new Date(enrollment.createdAt).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const lastStr = new Date(enrollment.lastActivityAt).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const installation = enrollment.installation;
  const hasCustomServer = !!installation?.authUrl;

  return (
    <View style={styles.container}>
      <View style={styles.identityZone}>
        <Text style={styles.installationLine}>
          {installation?.name ?? t('enrollmentDetail.installationFallback')}
        </Text>
        {enrollment.tenantName ? (
          <Text style={styles.tenantLine}>{enrollment.tenantName}</Text>
        ) : null}
        <Text style={styles.integrationName}>{enrollment.integrationName}</Text>
        {enrollment.enrollmentName ? (
          <Text style={styles.deviceLine}>{enrollment.enrollmentName}</Text>
        ) : null}
        {installation?.description ? (
          <Text style={styles.descriptionLine}>{installation.description}</Text>
        ) : null}
      </View>

      <View style={styles.metaZone}>
        <Text style={styles.metaLine}>{t('enrollmentDetail.createdAt', {value: createdStr})}</Text>
        <Text style={styles.metaLine}>
          {t('enrollmentDetail.lastVerificationAt', {value: lastStr})}
        </Text>
      </View>

      {hasCustomServer ? (
        <View style={styles.serverZone}>
          <Text style={styles.serverLabel}>{t('enrollmentDetail.server')}</Text>
          <Text style={styles.serverValue}>{installation?.authUrl}</Text>
        </View>
      ) : null}

      {inlineFeedback ? (
        <View style={styles.feedbackBanner}>
          <Text style={styles.feedbackText}>{inlineFeedback}</Text>
        </View>
      ) : null}

      <TouchableOpacity
        style={[styles.primaryButton, isCheckingPending ? styles.disabledButton : undefined]}
        onPress={handleCheckPending}
        disabled={isCheckingPending}>
        <Text style={styles.primaryLabel}>
          {isCheckingPending ? t('enrollmentDetail.checkingPending') : t('enrollmentDetail.checkPending')}
        </Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    gap: 16,
    backgroundColor: '#0b0d11',
  },
  identityZone: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.15)',
  },
  installationLine: {
    fontSize: 14,
    fontWeight: '600',
    color: '#dfe6f7',
  },
  integrationName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
    marginTop: 10,
  },
  tenantLine: {
    fontSize: 14,
    color: '#9aa3b6',
    marginTop: 6,
  },
  deviceLine: {
    fontSize: 13,
    color: '#c2c8d5',
    marginTop: 2,
  },
  descriptionLine: {
    fontSize: 13,
    color: '#9aa3b6',
    marginTop: 10,
    lineHeight: 18,
  },
  metaZone: {
    paddingHorizontal: 4,
    gap: 4,
  },
  metaLine: {
    fontSize: 12,
    color: '#9aa3b6',
    lineHeight: 18,
  },
  serverZone: {
    backgroundColor: '#0f1628',
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.2)',
  },
  serverLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: '#5a7aa8',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 4,
  },
  serverValue: {
    fontSize: 12,
    color: '#5a9cf7',
  },
  feedbackBanner: {
    backgroundColor: 'rgba(54, 115, 223, 0.12)',
    borderLeftWidth: 4,
    borderLeftColor: '#5a9cf7',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  feedbackText: {
    fontSize: 14,
    color: '#d6e6ff',
    lineHeight: 20,
  },
  primaryButton: {
    backgroundColor: '#3076df',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  disabledButton: {
    opacity: 0.7,
  },
  primaryLabel: {
    fontSize: 17,
    fontWeight: '600',
    color: '#ffffff',
  },
  missingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 16,
    backgroundColor: '#0b0d11',
  },
  missingText: {
    fontSize: 16,
    color: '#f4f7ff',
    textAlign: 'center',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0b0d11',
  },
});
