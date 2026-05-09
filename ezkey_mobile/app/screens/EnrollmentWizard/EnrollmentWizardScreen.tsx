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
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useTranslation} from 'react-i18next';
import {useCameraPermission} from 'react-native-vision-camera';
import {RootStackParamList} from '../../navigation/types';
import {EnrollmentScannerModal} from '../../components/EnrollmentScannerModal';
import PinCodeInput from '../../components/PinCodeInput';
import {useEnrollmentWizard} from '../../hooks/useEnrollmentWizard';

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
}) => {
  const {t} = useTranslation();

  return (
    <View style={[styles.infoCard, compact && styles.infoCardCompact]}>
      <View style={[styles.infoCardHeader, compact && styles.infoCardHeaderCompact]}>
        <Text style={styles.infoCardTitle}>{draft.integrationName}</Text>
      </View>
      <View style={[styles.infoCardBody, compact && styles.infoCardBodyCompact]}>
        {draft.integrationDescription ? (
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>{t('enrollmentWizard.description')}</Text>
            <Text style={styles.infoValue}>{draft.integrationDescription}</Text>
          </View>
        ) : null}
        {draft.tenantName ? (
          <>
            <View style={styles.infoDivider} />
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>{t('enrollmentWizard.organization')}</Text>
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
              <Text style={styles.infoLabel}>{t('enrollmentWizard.device')}</Text>
              <Text style={styles.infoValue}>{draft.enrollmentName}</Text>
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
    handleScannerDismiss,
  } = useEnrollmentWizard(navigation.popToTop);

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
          <TouchableOpacity
            onPress={() => handleBack(navigation.goBack)}
            style={styles.backButton}>
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
              </View>
            </>
          ) : null}
          {hasDraft && draft ? (
            <>
              <Text style={styles.flowSectionLabel}>{t('enrollmentWizard.verifyLabel')}</Text>
              <View style={styles.challengeSection}>
                <Text style={styles.challengeHeading}>{t('enrollmentWizard.verifyTitle')}</Text>
                <Text style={styles.challengeHint}>
                  {t('enrollmentWizard.verifyHint', {name: draft.integrationName})}
                </Text>
                <PinCodeInput
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
              </View>
              <Text style={styles.enrollmentDetailsLabel}>{t('enrollmentWizard.enrollmentDetails')}</Text>
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
          onPress={() => handlePrimary(hasCameraPermission, requestPermission)}
          disabled={primaryDisabled}>
          <Text style={styles.primaryLabel}>{primaryLabel}</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
      <EnrollmentScannerModal
        visible={scannerVisible}
        onDismiss={handleScannerDismiss}
        onScanned={handleQrScanned}
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
    color: '#ff7878',
    fontSize: 13,
    lineHeight: 18,
  },
  scanInstructions: {
    marginTop: 16,
  },
  infoCard: {
    borderRadius: 12,
    backgroundColor: '#111620',
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.18)',
    overflow: 'hidden',
    marginTop: 8,
  },
  infoCardCompact: {
    marginTop: 0,
  },
  infoCardHeader: {
    backgroundColor: 'rgba(54, 115, 223, 0.12)',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  infoCardHeaderCompact: {
    paddingVertical: 8,
  },
  infoCardTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#cdd9f7',
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
    color: '#5a7aa8',
    fontWeight: '500',
    flexShrink: 0,
  },
  infoValue: {
    fontSize: 12,
    color: '#c2c8d5',
    textAlign: 'right',
    flexShrink: 1,
  },
  infoValueMuted: {
    fontSize: 11,
    color: '#6b7a96',
    marginTop: 2,
    textAlign: 'right',
  },
  infoValueSmall: {
    fontSize: 11,
    color: '#c2c8d5',
    textAlign: 'right',
    flexShrink: 1,
  },
  infoDivider: {
    height: 1,
    backgroundColor: 'rgba(54, 115, 223, 0.1)',
    marginVertical: 2,
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 20,
    paddingBottom: 32,
    paddingTop: 12,
  },
  primaryButton: {
    flex: 1,
    backgroundColor: '#3673df',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  secondaryButton: {
    flex: 1,
    backgroundColor: '#151923',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.3)',
  },
  disabledButton: {
    opacity: 0.5,
  },
  secondaryLabel: {
    color: '#9aa3b6',
    fontSize: 15,
    fontWeight: '600',
  },
  primaryLabel: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '600',
  },
});
