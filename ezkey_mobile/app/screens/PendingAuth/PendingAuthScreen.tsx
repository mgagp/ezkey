/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: PendingAuthScreen
 * Description: React Native screen orchestrating the device-side pending and respond flows.
 *              All business logic is delegated to {@link usePendingAuth}.
 * Security Context: Embeds the polling model, proof token usage, and read-once semantics described in
 *                   docs/features/AUTH_SECURITY.md to keep user actions intentional and replay resistant.
 * UX: Approve and Deny. Leaving the screen with a loaded pending attempt (hardware back / gesture)
 *     is treated as Deny via Auth API respond(accepted=false) so Admin login wait unblocks (#831).
 *     Do not leave PENDING until TTL when the user explicitly abandons the request.
 * @since 2025
 */

import React, {useCallback, useEffect} from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useTranslation} from 'react-i18next';
import {env} from '../../config/env';
import {RootStackParamList} from '../../navigation/types';
import PinCodeInput from '../../components/PinCodeInput';
import {usePendingAuth, AUTH_CHALLENGE_LENGTH, tracePendingAuthRespond} from '../../hooks/usePendingAuth';
import {useSensitiveScreen} from '../../hooks/useSensitiveScreen';
import {useAccessibilityAnnouncement} from '../../hooks/useAccessibilityAnnouncement';
import {colors} from '../../config/theme';
import {
  buildEnrollmentIdentityDisplay,
  identityDisplayCopy,
} from '../../utils/enrollmentDisplay';

type Props = NativeStackScreenProps<RootStackParamList, 'PendingAuth'>;

/**
 * Presents the current pending authentication attempt for a selected enrollment and enables the user to accept or deny it.
 * All business logic (load, crypto, sign, verify) is handled by {@link usePendingAuth}.
 *
 * @param route React Navigation route containing the target enrollment identifier.
 * @since 2025
 */
export const PendingAuthScreen: React.FC<Props> = ({route, navigation}) => {
  const {t} = useTranslation();
  const {enrollmentId, initialAttempt} = route.params;

  // No screenshots, overlays or obscured touches while a decision is on screen (#1154).
  useSensitiveScreen(navigation);

  const navigateToEnrollmentDetail = useCallback(
    () => navigation.navigate('EnrollmentDetail', {enrollmentId}),
    [navigation, enrollmentId],
  );

  const {
    enrollment,
    isEnrollmentLoading,
    attempt,
    challengeInput,
    setChallengeInput,
    formError,
    setFormError,
    globalError,
    globalErrorRetryable,
    isProcessing,
    loading,
    debugInfo,
    showEmptyState,
    primaryTitle,
    secondaryTitle,
    roleLabel,
    loadPendingAttempt,
    handleRespond,
    hasSubmittedServerDecision,
    isRespondInFlight,
    dismissRetryableErrorKeepAttempt,
  } = usePendingAuth(enrollmentId, initialAttempt, {
    canGoBack: () => navigation.canGoBack(),
    goBack: () => navigation.goBack(),
    navigateToEnrollmentDetail,
  });

  // Leave with a loaded pending attempt → Auth API deny so passwordless-wait unblocks (#831).
  // Skip when approve/deny is already in flight (sync ref) so leave cannot send a second deny (#833 m2).
  // isRespondInFlight already covers the isProcessing window — do not also check isProcessing.
  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', e => {
      if (!attempt || hasSubmittedServerDecision() || isRespondInFlight()) {
        return;
      }
      e.preventDefault();
      tracePendingAuthRespond('pendingAuth_leave_as_deny', {
        authAttemptId: attempt.authAttemptId,
      });
      void handleRespond(false).finally(() => {
        // Successful deny already navigates via handleReturnToEnrollmentDetail.
        // On failure, still allow leave so the user is not trapped on this screen.
        if (!hasSubmittedServerDecision()) {
          navigation.dispatch(e.data.action);
        }
      });
    });
    return unsubscribe;
  }, [navigation, attempt, handleRespond, hasSubmittedServerDecision, isRespondInFlight]);

  useAccessibilityAnnouncement(globalError);
  useAccessibilityAnnouncement(formError);

  const handleInactiveBack = useCallback(() => {
    if (navigation.canGoBack()) {
      navigation.goBack();
      return;
    }
    navigateToEnrollmentDetail();
  }, [navigation, navigateToEnrollmentDetail]);

  const approveChallengeIncomplete =
    Boolean(attempt?.challengeRequired) &&
    challengeInput.trim().length !== AUTH_CHALLENGE_LENGTH;

  const emptyIdentity = enrollment
    ? buildEnrollmentIdentityDisplay(
        enrollment,
        t('enrollmentDetail.installationFallback'),
        identityDisplayCopy(t),
      )
    : undefined;

  return (
    <View style={styles.container}>
      {!attempt && emptyIdentity ? (
        <View style={styles.enrollmentBox}>
          <Text style={styles.enrollmentIntegration}>{emptyIdentity.heroTitle}</Text>
          {emptyIdentity.purposeLabel ? (
            <Text style={styles.enrollmentTenant}>{emptyIdentity.purposeLabel}</Text>
          ) : null}
          {emptyIdentity.roleLabel ? (
            <Text style={styles.enrollmentTenant}>{emptyIdentity.roleLabel}</Text>
          ) : null}
          {emptyIdentity.tenantLabel ? (
            <Text style={styles.enrollmentTenant}>{emptyIdentity.tenantLabel}</Text>
          ) : null}
          {emptyIdentity.installationContext ? (
            <Text style={styles.enrollmentTenant}>{emptyIdentity.installationContext}</Text>
          ) : null}
        </View>
      ) : null}
      {isEnrollmentLoading || loading ? (
        <View style={styles.loading}>
          <ActivityIndicator />
          <Text style={styles.loadingText}>{t('pendingAuth.loading')}</Text>
        </View>
      ) : globalError ? (
        <View style={styles.errorState} testID="ezkey.e2e.pendingAuth.globalErrorState">
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
          {globalErrorRetryable ? (
            <TouchableOpacity
              testID="ezkey.e2e.pendingAuth.tryAgain"
              onPress={
                // 503 (and other retryable respond errors) keep the loaded attempt — do not
                // re-claim pending (would often land on empty while READ still waits) (#833).
                attempt ? dismissRetryableErrorKeepAttempt : loadPendingAttempt
              }
              style={styles.secondaryButton}
              accessibilityRole="button">
              <Text style={styles.secondaryLabel}>{t('pendingAuth.tryAgain')}</Text>
            </TouchableOpacity>
          ) : (
            <TouchableOpacity
              testID="ezkey.e2e.pendingAuth.inactiveBack"
              onPress={handleInactiveBack}
              style={styles.secondaryButton}
              accessibilityRole="button">
              <Text style={styles.secondaryLabel}>{t('common.back')}</Text>
            </TouchableOpacity>
          )}
        </View>
      ) : showEmptyState ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>{t('pendingAuth.noPending')}</Text>
          <TouchableOpacity
            testID="ezkey.e2e.pendingAuth.checkAgain"
            onPress={loadPendingAttempt}
            style={styles.checkAgainButton}
            accessibilityRole="button">
            <Text style={styles.checkAgainLabel}>{t('pendingAuth.checkAgain')}</Text>
          </TouchableOpacity>
        </View>
      ) : (
        attempt && (
          <ScrollView
            testID="ezkey.e2e.pendingAuth.attemptScroll"
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
            contentContainerStyle={styles.pendingScrollContent}>
            <View style={styles.card}>
              <View style={styles.cardHeader}>
                <Text style={styles.cardStatusBadge}>{t('pendingAuth.pendingSuffix')}</Text>
                <Text style={styles.cardTitle}>{primaryTitle}</Text>
              </View>

              {secondaryTitle ? (
                <Text style={styles.cardSubtitle}>{secondaryTitle}</Text>
              ) : null}
              {roleLabel ? <Text style={styles.cardSubtitle}>{roleLabel}</Text> : null}

              {attempt.contextMessage ? (
                <View style={[styles.contextMessageBox, styles.borderInfo]}>
                  <Text style={styles.contextMessageText}>{attempt.contextMessage}</Text>
                </View>
              ) : null}

              {attempt.challengeRequired ? (
                <View style={styles.challengeSection}>
                  <Text style={styles.challengeHeading}>{t('pendingAuth.challengeHeading')}</Text>
                  <PinCodeInput
                    length={AUTH_CHALLENGE_LENGTH}
                    value={challengeInput}
                    onChangeText={setChallengeInput}
                    onClearError={() => setFormError(undefined)}
                    editable={!isProcessing}
                    autoFocus
                    testID="ezkey.e2e.pendingAuth.challengeDigits"
                    accessibilityLabel={t('pendingAuth.challengeInput')}
                    accessibilityHint={t('pendingAuth.challengeInputHint')}
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
                  testID="ezkey.e2e.pendingAuth.deny"
                  onPress={async () => {
                    tracePendingAuthRespond('pendingAuth_ui_deny_press', {});
                    await handleRespond(false);
                  }}
                  style={[styles.actionButton, styles.rejectButton]}
                  disabled={isProcessing}
                  accessibilityRole="button"
                  accessibilityState={{disabled: isProcessing}}>
                  <Text style={styles.rejectLabel}>{t('pendingAuth.deny')}</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  testID="ezkey.e2e.pendingAuth.approve"
                  onPress={async () => {
                    tracePendingAuthRespond('pendingAuth_ui_approve_press', {});
                    await handleRespond(true);
                  }}
                  style={[
                    styles.actionButton,
                    approveChallengeIncomplete ? styles.approveButtonDisabled : styles.approveButton,
                  ]}
                  disabled={isProcessing || approveChallengeIncomplete}
                  accessibilityRole="button"
                  accessibilityState={{
                    disabled: isProcessing || approveChallengeIncomplete,
                    busy: isProcessing,
                  }}>
                  <Text
                    style={
                      approveChallengeIncomplete ? styles.approveLabelDisabled : styles.approveLabel
                    }>
                    {isProcessing ? t('pendingAuth.sending') : t('pendingAuth.approve')}
                  </Text>
                </TouchableOpacity>
              </View>
              {!isProcessing ? (
                <Text
                  testID="ezkey.e2e.pendingAuth.leaveDeclinesHint"
                  style={styles.leaveDeclinesHint}>
                  {t('pendingAuth.leaveDeclinesHint')}
                </Text>
              ) : null}
              {isProcessing ? (
                <View
                  testID="ezkey.e2e.pendingAuth.respondInFlight"
                  collapsable={false}
                  style={styles.respondInFlightMarker}
                  importantForAccessibility="no"
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
    backgroundColor: colors.background,
  },
  pendingScrollContent: {
    paddingBottom: 24,
    flexGrow: 1,
  },
  enrollmentBox: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  enrollmentIntegration: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  enrollmentTenant: {
    fontSize: 14,
    color: colors.textMuted,
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
    color: colors.textMuted,
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
    color: colors.textPrimary,
    textAlign: 'center',
  },
  checkAgainButton: {
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 24,
    marginTop: 12,
  },
  checkAgainLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.textOnPrimary,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 16,
    padding: 20,
    gap: 16,
  },
  cardHeader: {
    gap: 8,
  },
  cardStatusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: colors.successBg,
    color: colors.success,
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  cardSubtitle: {
    fontSize: 14,
    color: colors.textSecondary,
  },
  challengeSection: {
    gap: 12,
  },
  challengeHeading: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.textPrimary,
    lineHeight: 24,
  },
  errorBanner: {
    backgroundColor: colors.errorBg,
    borderLeftWidth: 4,
    borderLeftColor: colors.error,
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  errorBannerText: {
    fontSize: 14,
    color: colors.error,
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
  },
  leaveDeclinesHint: {
    fontSize: 13,
    color: colors.textMuted,
    textAlign: 'center',
    lineHeight: 18,
  },
  respondInFlightMarker: {
    alignSelf: 'stretch',
    height: 2,
    marginTop: 6,
    opacity: 0.06,
    backgroundColor: colors.textOnPrimary,
  },
  actionButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  rejectButton: {
    borderWidth: 1,
    borderColor: colors.errorLight,
  },
  approveButton: {
    backgroundColor: colors.success,
  },
  approveButtonDisabled: {
    borderWidth: 1,
    borderColor: colors.successBorderMuted,
    backgroundColor: 'transparent',
  },
  rejectLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.errorLight,
  },
  approveLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.textOnSuccess,
  },
  approveLabelDisabled: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.successTextMuted,
  },
  secondaryButton: {
    alignItems: 'center',
    lineHeight: 18,
  },
  secondaryLabel: {
    fontSize: 14,
    color: colors.textMuted,
  },
  /* Context message box */
  contextMessageBox: {
    borderLeftWidth: 3,
    paddingLeft: 12,
    paddingVertical: 8,
    backgroundColor: colors.surfaceElevated,
    borderRadius: 8,
  },
  contextMessageText: {
    fontSize: 14,
    color: colors.textBody,
    lineHeight: 20,
  },
  /* Context level border accents */
  borderInfo: {
    borderLeftColor: colors.success,
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
    color: colors.textPrimary,
  },
  errorBody: {
    fontSize: 14,
    color: colors.error,
    textAlign: 'center',
  },
  debugBox: {
    alignSelf: 'stretch',
    backgroundColor: colors.surface,
    borderRadius: 8,
    padding: 12,
    marginTop: 8,
    borderWidth: 1,
    borderColor: colors.borderMuted,
  },
  debugTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textMuted,
    marginBottom: 4,
  },
  debugTimestamp: {
    fontSize: 11,
    color: colors.success,
    fontFamily: 'monospace',
    marginBottom: 8,
  },
  debugLine: {
    fontSize: 11,
    color: colors.textMuted,
    fontFamily: 'monospace',
    marginTop: 2,
  },
});
