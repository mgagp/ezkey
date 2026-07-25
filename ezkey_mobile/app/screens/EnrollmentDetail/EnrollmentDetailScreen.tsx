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

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  Alert,
  Button,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import axios from 'axios';
import {useTranslation} from 'react-i18next';
import {
  useDeleteEnrollment,
  useEnrollments,
  useMarkEnrollmentPendingChecked,
} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {claimPendingAttempt} from '../../services/pendingAuth/claimPendingAttempt';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

/**
 * Screen that surfaces enrollment metadata, owns pending checks, and shows the latest verified local response summary.
 * Primary action: Check pending. Delete moved to Danger Zone.
 *
 * @since 2025
 */
export const EnrollmentDetailScreen: React.FC<Props> = ({route, navigation}) => {
  const {t} = useTranslation();
  const {enrollmentId} = route.params;
  const {data, isLoading} = useEnrollments();
  const deleteEnrollment = useDeleteEnrollment();
  const markEnrollmentPendingChecked = useMarkEnrollmentPendingChecked();
  const selectedId = useEnrollmentStore(store => store.selectedId);
  const recentAuthResult = useEnrollmentStore(store =>
    enrollmentId ? store.recentAuthResults[enrollmentId] : undefined,
  );
  const targetId = enrollmentId ?? selectedId;
  const [isCheckingPending, setIsCheckingPending] = useState(false);
  const [inlineFeedback, setInlineFeedback] = useState<string | undefined>();

  const enrollment = useMemo(() => {
    if (!targetId || !data) {
      return undefined;
    }
    return data.enrollments.find(item => item.id === targetId);
  }, [data, targetId]);

  const brokenEnrollment = useMemo(() => {
    if (!targetId || !data) {
      return undefined;
    }
    return data.broken.find(item => item.id === targetId);
  }, [data, targetId]);

  useEffect(() => {
    const title = enrollment?.integrationName ?? brokenEnrollment?.metadata.integrationName;
    if (title) {
      navigation.setOptions({title});
    }
  }, [enrollment, brokenEnrollment, navigation]);

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

      const result = await claimPendingAttempt(enrollment, {checkedAt});

      if (result.kind === 'none') {
        setInlineFeedback(t('pendingAuth.noPending'));
        return;
      }

      if (result.kind === 'fail_closed') {
        setInlineFeedback(
          result.reason === 'missing_integration_public_key'
            ? t('pendingAuth.missingPendingPublicKey')
            : result.reason === 'malformed_pending_response'
              ? t('pendingAuth.malformedPendingResponse')
              : t('pendingAuth.invalidPendingSignature'),
        );
        return;
      }

      navigation.navigate('PendingAuth', {
        enrollmentId,
        initialAttempt: result.attempt,
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

  if (isLoading) {
    return (
      <View
        style={styles.loadingContainer}
        testID="ezkey.e2e.enrollmentDetail.loading"
        collapsable={false}
        accessibilityLabel={t('enrollmentDetail.loading')}>
        <ActivityIndicator accessibilityLabel={t('common.loading')} />
      </View>
    );
  }

  if (!enrollment && brokenEnrollment) {
    // Fail-open visibility, fail-closed auth: the enrollment stays identifiable but no
    // pending/respond action is offered (MOB-015 locked UI contract).
    const metadata = brokenEnrollment.metadata;
    const handleRemove = () => {
      Alert.alert(
        t('home.brokenRemoveConfirmTitle'),
        t('home.brokenRemoveConfirmMessage', {name: metadata.integrationName}),
        [
          {text: t('home.brokenRemoveCancel'), style: 'cancel'},
          {
            text: t('home.brokenRowRemove'),
            style: 'destructive',
            onPress: async () => {
              try {
                await deleteEnrollment.mutateAsync(metadata.id);
                navigation.popToTop();
              } catch (error) {
                console.warn('[EnrollmentDetail] Failed to remove unusable enrollment:', error);
              }
            },
          },
        ],
      );
    };

    return (
      <View style={styles.container} testID="ezkey.e2e.enrollmentDetail.unusable">
        <View style={styles.identityZone}>
          <Text style={styles.installationLine}>
            {metadata.installation?.name ?? t('enrollmentDetail.installationFallback')}
          </Text>
          {metadata.tenantName ? (
            <Text style={styles.tenantLine}>{metadata.tenantName}</Text>
          ) : null}
          <Text style={styles.integrationName}>{metadata.integrationName}</Text>
          {metadata.enrollmentName ? (
            <Text style={styles.deviceLine}>{metadata.enrollmentName}</Text>
          ) : null}
        </View>

        <View style={styles.unusableBanner}>
          <Text style={styles.unusableTitle}>{t('enrollmentDetail.unusableTitle')}</Text>
          <Text style={styles.unusableBody}>{t('enrollmentDetail.unusableBody')}</Text>
        </View>

        <TouchableOpacity
          testID="ezkey.e2e.enrollmentDetail.removeUnusable"
          style={styles.removeButton}
          onPress={handleRemove}
          disabled={deleteEnrollment.isPending}
          accessibilityRole="button"
          accessibilityLabel={t('enrollmentDetail.removeEnrollment')}>
          <Text style={styles.removeLabel}>{t('enrollmentDetail.removeEnrollment')}</Text>
        </TouchableOpacity>

        <Button title={t('enrollmentDetail.backToHome')} onPress={() => navigation.popToTop()} />
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
  const recentResultTimeStr = recentAuthResult
    ? new Date(recentAuthResult.completedAt).toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      })
    : undefined;
  const recentResultStatusLabel = recentAuthResult
    ? recentAuthResult.status === 'approved'
      ? t('enrollmentDetail.recentActionApproved')
      : recentAuthResult.status === 'rejected'
        ? t('enrollmentDetail.recentActionRejected')
        : t('enrollmentDetail.recentActionFailed')
    : undefined;
  const recentResultBadgeStyle = recentAuthResult
    ? recentAuthResult.status === 'approved'
      ? styles.recentActionBadgeApproved
      : recentAuthResult.status === 'rejected'
        ? styles.recentActionBadgeRejected
        : styles.recentActionBadgeFailed
    : undefined;

  return (
    <View style={styles.container} testID="ezkey.e2e.enrollmentDetail.screen">
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
        testID="ezkey.e2e.enrollmentDetail.checkPending"
        style={[styles.primaryButton, isCheckingPending ? styles.disabledButton : undefined]}
        onPress={handleCheckPending}
        disabled={isCheckingPending}>
        <Text style={styles.primaryLabel}>
          {isCheckingPending ? t('enrollmentDetail.checkingPending') : t('enrollmentDetail.checkPending')}
        </Text>
      </TouchableOpacity>

      {recentAuthResult ? (
        <View style={styles.recentActionCard}>
          <Text style={styles.recentActionEyebrow}>{t('enrollmentDetail.recentActionLabel')}</Text>
          <Text style={styles.recentActionTitleLine}>
            {recentAuthResult.title}
            <Text style={[styles.recentActionBadge, recentResultBadgeStyle]}>
              {' '}
              {recentResultStatusLabel}
            </Text>
          </Text>
          {recentAuthResult.message ? (
            <Text style={styles.recentActionMessage}>{recentAuthResult.message}</Text>
          ) : null}
          {recentResultTimeStr ? (
            <Text style={styles.recentActionMeta}>
              {t('enrollmentDetail.recentActionAt', {value: recentResultTimeStr})}
            </Text>
          ) : null}
        </View>
      ) : null}
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
  recentActionCard: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.18)',
    gap: 8,
  },
  recentActionEyebrow: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    color: '#9aa3b6',
  },
  recentActionTitleLine: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  recentActionBadge: {
    fontSize: 16,
    fontWeight: '700',
  },
  recentActionBadgeApproved: {
    color: '#61d095',
  },
  recentActionBadgeRejected: {
    color: '#ff8d8d',
  },
  recentActionBadgeFailed: {
    color: '#f5c26b',
  },
  recentActionMessage: {
    fontSize: 14,
    lineHeight: 20,
    color: '#dfe6f7',
  },
  recentActionMeta: {
    fontSize: 13,
    color: '#9aa3b6',
  },
  unusableBanner: {
    backgroundColor: 'rgba(245, 194, 107, 0.08)',
    borderLeftWidth: 4,
    borderLeftColor: '#f5c26b',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    gap: 6,
  },
  unusableTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#f5c26b',
  },
  unusableBody: {
    fontSize: 14,
    color: '#dfe6f7',
    lineHeight: 20,
  },
  removeButton: {
    backgroundColor: '#c24b4b',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  removeLabel: {
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
