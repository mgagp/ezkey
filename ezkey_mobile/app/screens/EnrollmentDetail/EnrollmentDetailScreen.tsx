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
import {ActivityIndicator, Alert, Button, Text, TouchableOpacity, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useTranslation} from 'react-i18next';
import {formatDateTime} from '../../utils/formatDateTime';
import {
  useDeleteEnrollment,
  useEnrollments,
  useMarkEnrollmentPendingChecked,
} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {
  isAuthAttemptBindingFailed,
  userFacingAuthApiError,
} from '../../services/api/authApiProblem';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {claimPendingAttempt} from '../../services/pendingAuth/claimPendingAttempt';
import {useAccessibilityAnnouncement} from '../../hooks/useAccessibilityAnnouncement';
import {createThemedStyles} from '../../theme';
import {
  buildEnrollmentIdentityDisplay,
  identityDisplayCopy,
} from '../../utils/enrollmentDisplay';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

/**
 * Screen that surfaces enrollment metadata, owns pending checks, and shows the latest verified local response summary.
 * Primary action: Check pending. Delete moved to Danger Zone.
 *
 * @since 2025
 */
export const EnrollmentDetailScreen: React.FC<Props> = ({route, navigation}) => {
  const styles = useStyles();
  const {t, i18n} = useTranslation();
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
  // Set when the last pending check got auth-attempt-binding-failed: the server no longer accepts
  // this local enrollment. Session-only (not persisted): the next successful check clears it.
  const [noLongerActive, setNoLongerActive] = useState(false);
  const recentResultStatusLabel = recentAuthResult
    ? recentAuthResult.status === 'approved'
      ? t('enrollmentDetail.recentActionApproved')
      : recentAuthResult.status === 'rejected'
        ? t('enrollmentDetail.recentActionRejected')
        : t('enrollmentDetail.recentActionFailed')
    : undefined;

  useAccessibilityAnnouncement(
    recentAuthResult?.status === 'failed' && recentAuthResult.message
      ? `${recentResultStatusLabel}. ${recentAuthResult.message}`
      : recentResultStatusLabel,
    recentAuthResult?.completedAt,
  );
  useAccessibilityAnnouncement(inlineFeedback);
  useAccessibilityAnnouncement(
    noLongerActive ? t('enrollmentDetail.noLongerActiveTitle') : undefined,
  );

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

  const identitySource = enrollment ?? brokenEnrollment?.metadata;
  const identity = useMemo(
    () =>
      identitySource
        ? buildEnrollmentIdentityDisplay(
            identitySource,
            t('enrollmentDetail.installationFallback'),
            identityDisplayCopy(t),
          )
        : undefined,
    [identitySource, t],
  );

  useEffect(() => {
    if (identity?.navTitle) {
      navigation.setOptions({title: identity.navTitle});
    }
  }, [identity, navigation]);

  const extractErrorMessage = useCallback(
    (error: unknown) => {
      const fallback =
        error instanceof Error ? t('pendingAuth.requestFailed') : t('pendingAuth.unexpectedError');
      return userFacingAuthApiError(error, fallback);
    },
    [t],
  );

  const handleCheckPending = useCallback(async () => {
    if (!enrollment || isCheckingPending) {
      return;
    }

    setInlineFeedback(undefined);
    setNoLongerActive(false);
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
      if (isAuthAttemptBindingFailed(error)) {
        setNoLongerActive(true);
      } else {
        setInlineFeedback(extractErrorMessage(error));
      }
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
          <Text style={styles.integrationName}>{identity?.heroTitle ?? metadata.integrationName}</Text>
          {identity?.purposeLabel ? (
            <Text style={styles.deviceLine}>{identity.purposeLabel}</Text>
          ) : null}
          {identity?.roleLabel ? (
            <Text style={styles.deviceLine}>{identity.roleLabel}</Text>
          ) : null}
          {identity?.tenantLabel ? (
            <Text style={styles.tenantLine}>{identity.tenantLabel}</Text>
          ) : null}
          {identity?.tenantDescription ? (
            <Text style={styles.tenantDescriptionLine}>{identity.tenantDescription}</Text>
          ) : null}
          {identity?.installationContext ? (
            <Text style={styles.installationLine}>{identity.installationContext}</Text>
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

  const lastStr = formatDateTime(enrollment.lastActivityAt, i18n.language);
  const resolvedIdentity =
    identity ??
    buildEnrollmentIdentityDisplay(
      enrollment,
      t('enrollmentDetail.installationFallback'),
      identityDisplayCopy(t),
    );
  const recentResultTimeStr = recentAuthResult
    ? formatDateTime(recentAuthResult.completedAt, i18n.language)
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
        <Text style={styles.integrationName} testID="ezkey.e2e.enrollmentDetail.hero">
          {resolvedIdentity.heroTitle}
        </Text>
        {resolvedIdentity.purposeLabel ? (
          <Text style={styles.deviceLine}>{resolvedIdentity.purposeLabel}</Text>
        ) : null}
        {resolvedIdentity.roleLabel ? (
          <Text style={styles.deviceLine}>{resolvedIdentity.roleLabel}</Text>
        ) : null}
        {resolvedIdentity.tenantLabel ? (
          <Text style={styles.tenantLine}>{resolvedIdentity.tenantLabel}</Text>
        ) : null}
        {resolvedIdentity.tenantDescription ? (
          <Text style={styles.tenantDescriptionLine}>{resolvedIdentity.tenantDescription}</Text>
        ) : null}
        {resolvedIdentity.installationContext ? (
          <Text style={styles.installationLine}>{resolvedIdentity.installationContext}</Text>
        ) : null}
      </View>

      <View style={styles.metaZone}>
        <Text style={styles.metaLine}>
          {t('enrollmentDetail.lastVerificationAt', {value: lastStr})}
        </Text>
      </View>

      {resolvedIdentity.showHostHint && resolvedIdentity.host ? (
        <View style={styles.serverZone} testID="ezkey.e2e.enrollmentDetail.hostHint">
          <Text style={styles.serverLabel}>{t('enrollmentDetail.server')}</Text>
          <Text style={styles.serverValue}>{resolvedIdentity.host}</Text>
        </View>
      ) : null}

      {inlineFeedback ? (
        <View style={styles.feedbackBanner}>
          <Text style={styles.feedbackText}>{inlineFeedback}</Text>
        </View>
      ) : null}

      {noLongerActive ? (
        <>
          <View style={styles.unusableBanner} testID="ezkey.e2e.enrollmentDetail.noLongerActive">
            <Text style={styles.unusableTitle}>{t('enrollmentDetail.noLongerActiveTitle')}</Text>
            <Text style={styles.unusableBody}>{t('enrollmentDetail.noLongerActiveBody')}</Text>
          </View>
          <TouchableOpacity
            testID="ezkey.e2e.enrollmentDetail.removeNoLongerActive"
            style={styles.removeButton}
            onPress={() =>
              // Pull-only posture: the server cannot wipe the phone, so removal stays the
              // person's decision, confirmed like the unusable-data path (MOB-015).
              Alert.alert(
                t('home.brokenRemoveConfirmTitle'),
                t('home.brokenRemoveConfirmMessage', {
                  name: resolvedIdentity.accountLabel ?? resolvedIdentity.heroTitle,
                }),
                [
                  {text: t('home.brokenRemoveCancel'), style: 'cancel'},
                  {
                    text: t('home.brokenRowRemove'),
                    style: 'destructive',
                    onPress: async () => {
                      try {
                        await deleteEnrollment.mutateAsync(enrollment.id);
                        navigation.popToTop();
                      } catch (error) {
                        console.warn('[EnrollmentDetail] Failed to remove inactive enrollment:', error);
                      }
                    },
                  },
                ],
              )
            }
            disabled={deleteEnrollment.isPending}
            accessibilityRole="button"
            accessibilityLabel={t('enrollmentDetail.removeEnrollment')}>
            <Text style={styles.removeLabel}>{t('enrollmentDetail.removeEnrollment')}</Text>
          </TouchableOpacity>
        </>
      ) : null}

      <TouchableOpacity
        testID="ezkey.e2e.enrollmentDetail.checkPending"
        style={[styles.primaryButton, isCheckingPending ? styles.disabledButton : undefined]}
        onPress={handleCheckPending}
        disabled={isCheckingPending}
        accessibilityRole="button"
        accessibilityState={{disabled: isCheckingPending, busy: isCheckingPending}}>
        <Text style={styles.primaryLabel}>
          {isCheckingPending ? t('enrollmentDetail.checkingPending') : t('enrollmentDetail.checkPending')}
        </Text>
      </TouchableOpacity>

      {recentAuthResult ? (
        <View style={styles.recentActionCard}>
          <Text style={styles.recentActionEyebrow}>{t('enrollmentDetail.recentActionLabel')}</Text>
          <Text
            style={[styles.recentActionTitleLine, recentResultBadgeStyle]}
            testID="ezkey.e2e.enrollmentDetail.recentActionStatus">
            {recentResultStatusLabel}
          </Text>
          {recentAuthResult.status === 'failed' && recentAuthResult.message ? (
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

const useStyles = createThemedStyles(colors => ({
  container: {
    flex: 1,
    padding: 20,
    gap: 16,
    backgroundColor: colors.background,
  },
  identityZone: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  installationLine: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.textBody,
  },
  integrationName: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  tenantLine: {
    fontSize: 14,
    color: colors.textMuted,
    marginTop: 6,
  },
  tenantDescriptionLine: {
    fontSize: 13,
    color: colors.textSubtle,
    marginTop: 4,
    lineHeight: 18,
  },
  deviceLine: {
    fontSize: 13,
    color: colors.textSecondary,
    marginTop: 6,
  },
  metaZone: {
    paddingHorizontal: 4,
    gap: 4,
  },
  metaLine: {
    fontSize: 12,
    color: colors.textMuted,
    lineHeight: 18,
  },
  serverZone: {
    backgroundColor: colors.surfaceMuted,
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.borderStrong,
  },
  serverLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: colors.primaryMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 4,
  },
  serverValue: {
    fontSize: 12,
    color: colors.primaryLight,
  },
  feedbackBanner: {
    backgroundColor: colors.primaryTint,
    borderLeftWidth: 4,
    borderLeftColor: colors.primaryLight,
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  feedbackText: {
    fontSize: 14,
    color: colors.textAccent,
    lineHeight: 20,
  },
  primaryButton: {
    backgroundColor: colors.primary,
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
    color: colors.textOnPrimary,
  },
  recentActionCard: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.borderCard,
    gap: 8,
  },
  recentActionEyebrow: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    color: colors.textMuted,
  },
  recentActionTitleLine: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  recentActionBadgeApproved: {
    color: colors.success,
  },
  recentActionBadgeRejected: {
    color: colors.errorSoft,
  },
  recentActionBadgeFailed: {
    color: colors.caution,
  },
  recentActionMessage: {
    fontSize: 14,
    lineHeight: 20,
    color: colors.textBody,
  },
  recentActionMeta: {
    fontSize: 13,
    color: colors.textMuted,
  },
  unusableBanner: {
    backgroundColor: colors.cautionBg,
    borderLeftWidth: 4,
    borderLeftColor: colors.caution,
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    gap: 6,
  },
  unusableTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.caution,
  },
  unusableBody: {
    fontSize: 14,
    color: colors.textBody,
    lineHeight: 20,
  },
  removeButton: {
    backgroundColor: colors.danger,
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  removeLabel: {
    fontSize: 17,
    fontWeight: '600',
    color: colors.textOnDanger,
  },
  missingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 16,
    backgroundColor: colors.background,
  },
  missingText: {
    fontSize: 16,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.background,
  },
}));
