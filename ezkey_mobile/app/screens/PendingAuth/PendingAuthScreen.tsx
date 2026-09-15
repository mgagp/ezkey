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
 * UX: Approve and Deny only — there is no mobile "cancel" API; leaving the screen without responding
 *     relies on server-side TTL expiry (see Auth API / Admin batch expiry). Do not add a Cancel
 *     button that implies a distinct server action without an endpoint.
 * @since 2025
 */

import React, {useCallback} from 'react';
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
import {buildEnrollmentIdentityDisplay} from '../../utils/enrollmentDisplay';

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
    isProcessing,
    loading,
    debugInfo,
    showEmptyState,
    primaryTitle,
    secondaryTitle,
    loadPendingAttempt,
    handleRespond,
  } = usePendingAuth(enrollmentId, initialAttempt, {
    canGoBack: () => navigation.canGoBack(),
    goBack: () => navigation.goBack(),
    navigateToEnrollmentDetail,
  });

  const emptyIdentity = enrollment
    ? buildEnrollmentIdentityDisplay(enrollment, t('enrollmentDetail.installationFallback'))
    : undefined;

  return (
    <View style={styles.container}>
      {!attempt && emptyIdentity ? (
        <View style={styles.enrollmentBox}>
          <Text style={styles.enrollmentIntegration}>{emptyIdentity.heroTitle}</Text>
          {emptyIdentity.integrationLabel ? (
            <Text style={styles.enrollmentTenant}>{emptyIdentity.integrationLabel}</Text>
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
          <TouchableOpacity
            testID="ezkey.e2e.pendingAuth.tryAgain"
            onPress={loadPendingAttempt}
            style={styles.secondaryButton}>
            <Text style={styles.secondaryLabel}>{t('pendingAuth.tryAgain')}</Text>
          </TouchableOpacity>
        </View>
      ) : showEmptyState ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>{t('pendingAuth.noPending')}</Text>
          <TouchableOpacity
            testID="ezkey.e2e.pendingAuth.checkAgain"
            onPress={loadPendingAttempt}
            style={styles.checkAgainButton}>
            <Text style={styles.checkAgainLabel}>{t('pendingAuth.checkAgain')}</Text>
          </TouchableOpacity>
        </View>
      ) : (
        attempt && (
          <ScrollView
            testID="ezkey.e2e.pendingAuth.attemptScroll"
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
                  disabled={isProcessing}>
                  <Text style={styles.rejectLabel}>{t('pendingAuth.deny')}</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  testID="ezkey.e2e.pendingAuth.approve"
                  onPress={async () => {
                    tracePendingAuthRespond('pendingAuth_ui_approve_press', {});
                    await handleRespond(true);
                  }}
                  style={[styles.actionButton, styles.approveButton]}
                  disabled={isProcessing}>
                  <Text style={styles.approveLabel}>
                    {isProcessing ? t('pendingAuth.sending') : t('pendingAuth.approve')}
                  </Text>
                </TouchableOpacity>
              </View>
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
  cardHeader: {
    gap: 8,
  },
  cardStatusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: 'rgba(97, 208, 149, 0.14)',
    color: '#61d095',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#f4f7ff',
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
  respondInFlightMarker: {
    alignSelf: 'stretch',
    height: 2,
    marginTop: 6,
    opacity: 0.06,
    backgroundColor: '#ffffff',
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
