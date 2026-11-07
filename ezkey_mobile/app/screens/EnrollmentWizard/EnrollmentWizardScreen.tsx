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

import React from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  ViewStyle,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useTranslation} from 'react-i18next';
import {useCameraPermission} from 'react-native-vision-camera';
import {RootStackParamList} from '../../navigation/types';
import {EnrollmentScannerModal} from '../../components/EnrollmentScannerModal';
import PinCodeInput from '../../components/PinCodeInput';
import {useEnrollmentWizard} from '../../hooks/useEnrollmentWizard';
import {useAccessibilityAnnouncement} from '../../hooks/useAccessibilityAnnouncement';
import {createThemedStyles, useTheme} from '../../theme';
import {
  buildEnrollmentIdentityDisplay,
  identityDisplayCopy,
  resolveEnrollmentPurpose,
} from '../../utils/enrollmentDisplay';

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
  isSystemIntegration?: boolean;
  adminType?: 'GLOBAL_ADMIN' | 'TENANT_ADMIN';
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
}) => {
  const styles = useStyles();
  const {t} = useTranslation();
  const copy = identityDisplayCopy(t);
  const identity = buildEnrollmentIdentityDisplay(
    draft,
    t('enrollmentWizard.integrationFallback'),
    copy,
  );
  const purposeTitle =
    resolveEnrollmentPurpose(draft, copy) ?? draft.integrationName;
  const showDescription =
    Boolean(draft.integrationDescription) && !draft.isSystemIntegration;

  return (
    <View style={[styles.infoCard, compact && styles.infoCardCompact]}>
      <View style={[styles.infoCardHeader, compact && styles.infoCardHeaderCompact]}>
        <Text style={styles.infoCardTitle}>{purposeTitle}</Text>
      </View>
      <View style={[styles.infoCardBody, compact && styles.infoCardBodyCompact]}>
        {showDescription ? (
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>{t('enrollmentWizard.description')}</Text>
            <Text style={styles.infoValue}>{draft.integrationDescription}</Text>
          </View>
        ) : null}
        {identity.tenantLabel ? (
          <>
            <View style={styles.infoDivider} />
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>{t('enrollmentWizard.organization')}</Text>
              <Text style={styles.infoValue}>{identity.tenantLabel}</Text>
            </View>
            {identity.tenantDescription ? (
              <Text style={styles.infoValueMuted}>{identity.tenantDescription}</Text>
            ) : null}
          </>
        ) : null}
        {identity.accountLabel ? (
          <>
            <View style={styles.infoDivider} />
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>{t('enrollmentWizard.account')}</Text>
              <Text style={styles.infoValue}>{identity.accountLabel}</Text>
            </View>
          </>
        ) : null}
        {identity.roleLabel ? (
          <>
            <View style={styles.infoDivider} />
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>{t('enrollmentWizard.role')}</Text>
              <Text style={styles.infoValue}>{identity.roleLabel}</Text>
            </View>
          </>
        ) : null}
        {showServerUrl && serverUrl ? (
          <>
            <View style={styles.infoDivider} />
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>{t('enrollmentWizard.server')}</Text>
              <Text style={styles.infoValueSmall}>{serverUrl}</Text>
            </View>
          </>
        ) : null}
      </View>
    </View>
  );
};

/**
 * Walks the user through the Ezkey device enrollment workflow.
 * All business logic is delegated to {@link useEnrollmentWizard}.
 *
 * @param navigation Stack navigation helper.
 * @since 2025
 */
export const EnrollmentWizardScreen: React.FC<Props> = ({navigation}) => {
  const {colors} = useTheme();
  const styles = useStyles();
  const {t} = useTranslation();
  const {hasPermission: hasCameraPermission, requestPermission} = useCameraPermission();

  const {
    draft,
    isSubmitting,
    bindError,
    cameraError,
    enrollmentChallenge,
    challengeError,
    scannerVisible,
    controlledBypassAvailable,
    controlledBypassUsed,
    controlledBypassSeedInput,
    primaryLabel,
    secondaryLabel,
    primaryDisabled,
    secondaryDisabled,
    hasDraft,
    setEnrollmentChallenge,
    setChallengeError,
    handlePrimary,
    handleSecondary,
    handleBack,
    handleQrScanned,
    setControlledBypassSeedInput,
    handleControlledBypass,
    handleScannerDismiss,
  } = useEnrollmentWizard(navigation.popToTop);

  useAccessibilityAnnouncement(bindError ?? cameraError);
  useAccessibilityAnnouncement(challengeError);

  const actionButtons = (containerStyle: ViewStyle) => (
    <View style={containerStyle}>
      <TouchableOpacity
        testID="ezkey.e2e.enrollmentWizard.secondaryAction"
        style={[styles.secondaryButton, secondaryDisabled ? styles.disabledButton : undefined]}
        onPress={handleSecondary}
        disabled={secondaryDisabled}
        accessibilityRole="button"
        accessibilityState={{disabled: secondaryDisabled}}>
        <Text style={styles.secondaryLabel}>{secondaryLabel}</Text>
      </TouchableOpacity>
      <TouchableOpacity
        testID="ezkey.e2e.enrollmentWizard.primaryAction"
        style={[styles.primaryButton, primaryDisabled ? styles.disabledButton : undefined]}
        onPress={() => handlePrimary(hasCameraPermission, requestPermission)}
        disabled={primaryDisabled}
        accessibilityRole="button"
        accessibilityState={{disabled: primaryDisabled, busy: isSubmitting}}>
        <Text style={styles.primaryLabel}>{primaryLabel}</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <>
    <KeyboardAvoidingView
      testID="ezkey.e2e.enrollmentWizard.screen"
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}>
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity
            testID="ezkey.e2e.enrollmentWizard.back"
            onPress={() => handleBack(navigation.goBack)}
            style={styles.backButton}
            accessibilityRole="button">
            <Text style={styles.backLabel}>{t('enrollmentWizard.back')}</Text>
          </TouchableOpacity>
          <View style={styles.backButton} />
        </View>
        <View style={styles.stepContainer}>
          {!hasDraft ? (
            <>
              <Text style={styles.flowSectionLabel}>{t('enrollmentWizard.scanLabel')}</Text>
              <Text style={styles.stepTitle}>{t('enrollmentWizard.scanTitle')}</Text>
              <Text style={styles.stepDescription}>{t('enrollmentWizard.scanDescription')}</Text>
              <View style={styles.scanInstructions}>
                {(bindError || cameraError) ? (
                  <View style={styles.errorBanner}>
                    <Text style={styles.errorBannerText}>{bindError ?? cameraError}</Text>
                  </View>
                ) : null}
                {controlledBypassAvailable ? (
                  <View style={styles.bypassContainer}>
                    <TextInput
                      testID="ezkey.e2e.enrollmentWizard.controlledBypassSeedInput"
                      value={controlledBypassSeedInput}
                      onChangeText={setControlledBypassSeedInput}
                      placeholder={t('enrollmentWizard.controlledBypassInputPlaceholder')}
                      placeholderTextColor={colors.textSubtle}
                      autoCapitalize="none"
                      autoCorrect={false}
                      style={styles.bypassInput}
                    />
                    <TouchableOpacity
                      testID="ezkey.e2e.enrollmentWizard.controlledBypassSeed"
                      style={styles.bypassButton}
                      onPress={handleControlledBypass}
                      accessibilityRole="button"
                      accessibilityLabel={t('enrollmentWizard.controlledBypassAction')}
                      accessibilityHint={t('enrollmentWizard.controlledBypassHint')}>
                      <Text style={styles.bypassButtonLabel}>
                        {t('enrollmentWizard.controlledBypassAction')}
                      </Text>
                    </TouchableOpacity>
                    <Text style={styles.bypassHint}>{t('enrollmentWizard.controlledBypassHint')}</Text>
                  </View>
                ) : null}
              </View>
            </>
          ) : null}
          {hasDraft && draft ? (
            <>
              <Text style={styles.flowSectionLabel}>{t('enrollmentWizard.verifyLabel')}</Text>
              <View
                testID="ezkey.e2e.enrollmentWizard.verifyStep"
                style={styles.challengeSection}>
                <Text style={styles.challengeHeading}>{t('enrollmentWizard.verifyTitle')}</Text>
                <Text style={styles.challengeHint}>
                  {t('enrollmentWizard.verifyHint', {
                    name:
                      resolveEnrollmentPurpose(draft, identityDisplayCopy(t)) ??
                      draft.integrationName,
                  })}
                </Text>
                <PinCodeInput
                  testID="ezkey.e2e.enrollmentWizard.challengeInput"
                  length={6}
                  centered
                  gap={8}
                  value={enrollmentChallenge}
                  onChangeText={value => setEnrollmentChallenge(value)}
                  onClearError={() => setChallengeError(undefined)}
                  editable={!isSubmitting}
                  accessibilityLabel={t('enrollmentWizard.challengeInput')}
                  accessibilityHint={t('enrollmentWizard.challengeInputHint')}
                />
                {challengeError ? (
                  <View style={styles.errorBanner}>
                    <Text style={styles.errorBannerText}>{challengeError}</Text>
                  </View>
                ) : null}
                {actionButtons(styles.actionsInline)}
              </View>
              <Text style={styles.enrollmentDetailsLabel}>{t('enrollmentWizard.enrollmentDetails')}</Text>
              {controlledBypassUsed ? (
                <View style={styles.bypassActiveBadge}>
                  <Text style={styles.bypassActiveBadgeLabel}>
                    {t('enrollmentWizard.controlledBypassActive')}
                  </Text>
                </View>
              ) : null}
              <EnrollmentInfoCard draft={draft} compact />
            </>
          ) : null}
        </View>
      </ScrollView>
      {hasDraft ? null : actionButtons(styles.actions)}
    </KeyboardAvoidingView>
      <EnrollmentScannerModal
        visible={scannerVisible}
        onDismiss={handleScannerDismiss}
        onScanned={handleQrScanned}
      />
    </>
  );
};

const useStyles = createThemedStyles(colors => ({
  container: {
    flex: 1,
    backgroundColor: colors.background,
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
    minHeight: 48,
    justifyContent: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  backLabel: {
    color: colors.primaryLight,
    fontSize: 14,
    fontWeight: '500',
  },
  flowSectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.primaryMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
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
    color: colors.textPrimary,
    marginBottom: 8,
    lineHeight: 24,
  },
  challengeHint: {
    fontSize: 14,
    color: colors.textMuted,
    marginBottom: 16,
    lineHeight: 20,
  },
  enrollmentDetailsLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.primaryMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
  },
  stepTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  stepDescription: {
    fontSize: 14,
    color: colors.textSecondary,
    marginTop: 8,
    marginBottom: 24,
  },
  bypassContainer: {
    marginTop: 8,
    gap: 8,
  },
  bypassButton: {
    borderWidth: 1,
    borderColor: colors.harnessAccent,
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 12,
    alignSelf: 'flex-start',
    backgroundColor: colors.harnessAccentBg,
  },
  bypassInput: {
    backgroundColor: colors.surface,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.harnessAccentBorder,
    color: colors.textAccent,
    fontSize: 12,
    lineHeight: 18,
    minHeight: 44,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  bypassButtonLabel: {
    color: colors.harnessAccentText,
    fontSize: 13,
    fontWeight: '600',
  },
  bypassHint: {
    color: colors.textSubtle,
    fontSize: 12,
    lineHeight: 18,
  },
  bypassActiveBadge: {
    alignSelf: 'flex-start',
    marginBottom: 10,
    borderRadius: 999,
    paddingVertical: 4,
    paddingHorizontal: 10,
    backgroundColor: colors.harnessWarningBg,
    borderWidth: 1,
    borderColor: colors.harnessWarningBorder,
  },
  bypassActiveBadgeLabel: {
    color: colors.harnessWarningText,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
  },
  errorBanner: {
    backgroundColor: colors.errorBg,
    borderLeftWidth: 4,
    borderLeftColor: colors.error,
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginTop: 4,
  },
  errorBannerText: {
    color: colors.errorLight,
    fontSize: 13,
    lineHeight: 18,
  },
  scanInstructions: {
    marginTop: 16,
  },
  infoCard: {
    borderRadius: 12,
    backgroundColor: colors.surfaceInfo,
    borderWidth: 1,
    borderColor: colors.borderCard,
    overflow: 'hidden',
    marginTop: 8,
  },
  infoCardCompact: {
    marginTop: 0,
  },
  infoCardHeader: {
    backgroundColor: colors.primaryTint,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  infoCardHeaderCompact: {
    paddingVertical: 8,
  },
  infoCardTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.textInfoTitle,
  },
  infoCardBody: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 6,
  },
  infoCardBodyCompact: {
    paddingVertical: 8,
    gap: 4,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 8,
  },
  infoLabel: {
    fontSize: 12,
    color: colors.primaryMuted,
    fontWeight: '500',
    flexShrink: 0,
  },
  infoValue: {
    fontSize: 12,
    color: colors.textSecondary,
    textAlign: 'right',
    flexShrink: 1,
  },
  infoValueMuted: {
    fontSize: 11,
    color: colors.textSubtle,
    marginTop: 2,
    textAlign: 'right',
  },
  infoValueSmall: {
    fontSize: 11,
    color: colors.textSecondary,
    textAlign: 'right',
    flexShrink: 1,
  },
  infoDivider: {
    height: 1,
    backgroundColor: colors.dividerAccent,
    marginVertical: 2,
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 20,
    paddingBottom: 32,
    paddingTop: 12,
  },
  actionsInline: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 20,
  },
  primaryButton: {
    flex: 1,
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  secondaryButton: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.borderControl,
  },
  disabledButton: {
    opacity: 0.5,
  },
  secondaryLabel: {
    color: colors.textMuted,
    fontSize: 15,
    fontWeight: '600',
  },
  primaryLabel: {
    color: colors.textOnPrimary,
    fontSize: 15,
    fontWeight: '600',
  },
}));
