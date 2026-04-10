import React, {useCallback, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useCameraPermission} from 'react-native-vision-camera';
import axios from 'axios';
import {RootStackParamList} from '../../navigation/types';
import {colors, spacing, typography, borderRadius} from '../../config/theme';
import {ChallengeInput} from '../../components/ChallengeInput';
import {EnrollmentScannerModal} from '../../components/EnrollmentScannerModal';
import {enrollmentsApi} from '../../services/api/enrollments';
import {BindEnrollmentResponse, EnrollmentStatus} from '../../services/api/types';
import {getCryptoService} from '../../services/crypto/cryptoService';
import {
  buildBindPayload,
  buildVerifyDevicePayload,
  buildVerifyResultPayload,
} from '../../services/crypto/enrollmentPayload';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {useSaveEnrollment} from '../../hooks/useEnrollments';
import {parseQrPayload} from '../../utils/parseQrPayload';

type Props = StackScreenProps<RootStackParamList, 'EnrollmentFlow'>;

type BindDraft = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName: string;
  tenantId?: number;
  tenantDescription?: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  integrationDescription?: string;
  enrollmentName?: string;
  deviceLabel?: string;
};

function buildDraft(
  response: BindEnrollmentResponse,
  request: {enrollmentId: string; enrollmentProofToken: string},
): BindDraft {
  const rawId = response.enrollmentId ?? request.enrollmentId;
  const id = String(rawId);
  return {
    id,
    integrationId: id,
    integrationName: response.integrationName ?? 'Integration',
    tenantName:
      response.tenantName ?? response.integrationDescription ?? 'Your organization',
    tenantId: response.tenantId,
    tenantDescription: response.tenantDescription,
    enrollmentProofToken: response.enrollmentProofToken ?? request.enrollmentProofToken,
    integrationPublicKey: response.integrationPublicKey,
    integrationDescription: response.integrationDescription,
    enrollmentName: response.enrollmentName,
    deviceLabel: response.enrollmentName,
  };
}

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.data?.message) {
    return String(error.response.data.message);
  }
  if (error instanceof Error) return error.message;
  return String(error);
}

export const EnrollmentFlowScreen: React.FC<Props> = ({navigation}) => {
  const insets = useSafeAreaInsets();
  const {hasPermission, requestPermission} = useCameraPermission();
  const saveEnrollment = useSaveEnrollment();

  const [scannerVisible, setScannerVisible] = useState(false);
  const [isBinding, setIsBinding] = useState(false);
  const [bindError, setBindError] = useState<string | null>(null);
  const [draft, setDraft] = useState<BindDraft | null>(null);
  const [authUrl, setAuthUrl] = useState<string | undefined>();
  const [challenge, setChallenge] = useState('');
  const [challengeError, setChallengeError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const onScanPress = useCallback(() => {
    setBindError(null);
    if (hasPermission) {
      setScannerVisible(true);
    } else {
      requestPermission().then((granted) => {
        if (granted) setScannerVisible(true);
        else setBindError('Camera access is required to scan the QR code.');
      });
    }
  }, [hasPermission, requestPermission]);

  const onScanned = useCallback(
    async (value: string) => {
      setScannerVisible(false);
      let parsed: {enrollmentId: string; enrollmentProofToken: string; language?: string; authUrl?: string};
      try {
        parsed = parseQrPayload(value);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        setBindError(`Invalid QR code: ${msg}`);
        return;
      }
      setAuthUrl(parsed.authUrl);
      setBindError(null);
      setIsBinding(true);
      setDraft(null);
      setChallenge('');
      try {
        const response = await enrollmentsApi.bind(
          {
            enrollmentId: parsed.enrollmentId,
            enrollmentProofToken: parsed.enrollmentProofToken,
            language: parsed.language,
          },
          parsed.authUrl,
        );
        const crypto = getCryptoService();
        const bindPayload = buildBindPayload(response);
        const bindOk = await crypto.verify(
          bindPayload,
          response.enrollmentBindPayloadSignedByIntegration,
          response.integrationPublicKey,
        );
        if (!bindOk) {
          setBindError('Could not verify server identity (integration signature).');
          return;
        }
        const nextDraft = buildDraft(response, {
          enrollmentId: parsed.enrollmentId,
          enrollmentProofToken: response.enrollmentProofToken ?? parsed.enrollmentProofToken,
        });
        setDraft(nextDraft);
      } catch (error) {
        setBindError(extractErrorMessage(error));
      } finally {
        setIsBinding(false);
      }
    },
    [],
  );

  const onComplete = useCallback(async () => {
    if (!draft || challenge.length !== 6) return;
    setChallengeError(null);
    setIsSubmitting(true);
    try {
      const crypto = getCryptoService();
      const enrollmentId = draft.id;
      await crypto.ensureEnrollmentKeyPair(enrollmentId);
      const publicKey = await crypto.getPublicKey(enrollmentId);
      const challengeNum = Number(challenge);
      const verifyDevicePayload = buildVerifyDevicePayload(
        draft.enrollmentProofToken,
        Number(draft.id),
        challengeNum,
        publicKey,
      );
      const proofTokenSigned = await crypto.sign(enrollmentId, verifyDevicePayload);
      const devicePrivateKeyStorageTier =
        await crypto.getDevicePrivateKeyStorageTier(enrollmentId);
      const verifyResponse = await enrollmentsApi.verify(
        {
          enrollmentId: draft.id,
          challengeResponse: challenge,
          devicePublicKey: publicKey,
          enrollmentProofTokenSigned: proofTokenSigned,
          devicePrivateKeyStorageTier,
        },
        authUrl,
      );
      const verifyResultPayload = buildVerifyResultPayload(
        draft.enrollmentProofToken,
        Number(draft.id),
        'VERIFIED',
        verifyResponse.enrollmentVerifyMessage,
      );
      const resultOk = await crypto.verify(
        verifyResultPayload,
        verifyResponse.enrollmentVerifyPayloadSignedByIntegration,
        draft.integrationPublicKey,
      );
      if (!resultOk) {
        setChallengeError('Could not verify enrollment result (integration signature).');
        setChallenge('');
        return;
      }
      const status: EnrollmentStatus = verifyResponse.active ? 'active' : 'pending';
      const now = new Date().toISOString();
      const record: StoredEnrollment = {
        id: draft.id,
        integrationId: draft.integrationId,
        integrationName: draft.integrationName,
        tenantName: draft.tenantName,
        tenantId: draft.tenantId,
        tenantDescription: draft.tenantDescription,
        createdAt: now,
        lastActivityAt: now,
        status,
        favorited: false,
        enrollmentProofToken: draft.enrollmentProofToken,
        enrollmentId,
        integrationPublicKey: draft.integrationPublicKey,
        enrollmentName: draft.enrollmentName,
        deviceLabel: draft.deviceLabel,
        authUrl,
        devicePrivateKeyStorageTier,
      };
      await saveEnrollment.mutateAsync(record);
      navigation.navigate('Home');
    } catch (error) {
      const msg = extractErrorMessage(error);
      setChallengeError(msg);
    } finally {
      setIsSubmitting(false);
    }
  }, [draft, challenge, authUrl, saveEnrollment, navigation]);

  const canComplete = Boolean(draft) && challenge.length === 6 && !isSubmitting;
  const showChallengePhase = Boolean(draft);

  return (
    <>
      <ScrollView
        style={styles.container}
        contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
        keyboardShouldPersistTaps="handled">
        {!showChallengePhase ? (
          <>
            <View style={styles.scannerWrap}>
              <TouchableOpacity
                style={styles.scannerPlaceholder}
                onPress={onScanPress}
                disabled={isBinding}>
                {isBinding ? (
                  <ActivityIndicator size="large" color={colors.primary} />
                ) : (
                  <>
                    <View style={styles.scannerFrame} />
                    <Text style={styles.scannerText}>Tap to scan QR code</Text>
                  </>
                )}
              </TouchableOpacity>
            </View>
            {bindError ? (
              <View style={styles.errorBanner}>
                <Text style={styles.errorText}>{bindError}</Text>
              </View>
            ) : null}
          </>
        ) : (
          <>
            <View style={styles.card}>
              <Text style={styles.cardTitle}>Enrollment details</Text>
              <Text style={styles.cardRow}>
                <Text style={styles.cardLabel}>Integration: </Text>
                {draft!.integrationName}
              </Text>
              <Text style={styles.cardRow}>
                <Text style={styles.cardLabel}>Organization: </Text>
                {draft!.tenantName}
              </Text>
              <Text style={styles.cardRow}>
                <Text style={styles.cardLabel}>Device: </Text>
                {draft!.enrollmentName ?? '—'}
              </Text>
            </View>
            <Text style={styles.challengeLabel}>Enter 6-digit code</Text>
            <ChallengeInput
              value={challenge}
              onChange={setChallenge}
              autoFocus={false}
              accessibilityLabel="Enrollment challenge code"
            />
            {challengeError ? (
              <Text style={styles.challengeErrorText}>{challengeError}</Text>
            ) : null}
            <TouchableOpacity
              style={[styles.button, !canComplete && styles.buttonDisabled]}
              onPress={onComplete}
              disabled={!canComplete}
              activeOpacity={0.85}>
              {isSubmitting ? (
                <ActivityIndicator color={colors.textOnPrimary} />
              ) : (
                <Text style={styles.buttonText}>Complete Enrollment</Text>
              )}
            </TouchableOpacity>
          </>
        )}
      </ScrollView>
      <EnrollmentScannerModal
        visible={scannerVisible}
        onDismiss={() => setScannerVisible(false)}
        onScanned={onScanned}
      />
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: spacing.xl,
  },
  scannerWrap: {
    marginBottom: spacing.xl,
  },
  scannerPlaceholder: {
    height: 220,
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scannerFrame: {
    width: 200,
    height: 200,
    borderWidth: 2,
    borderColor: colors.primary,
    borderRadius: 8,
    opacity: 0.6,
  },
  scannerText: {
    position: 'absolute',
    bottom: spacing.lg,
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
  },
  errorBanner: {
    backgroundColor: colors.errorBg,
    borderRadius: borderRadius.md,
    padding: spacing.md,
    marginBottom: spacing.lg,
  },
  errorText: {
    fontSize: typography.fontSize.sm,
    color: colors.error,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cardTitle: {
    fontSize: typography.fontSize.md,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.md,
  },
  cardRow: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
  },
  cardLabel: {
    color: colors.textMuted,
  },
  challengeLabel: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.md,
    textAlign: 'center',
  },
  challengeErrorText: {
    fontSize: typography.fontSize.sm,
    color: colors.error,
    marginTop: spacing.sm,
    textAlign: 'center',
  },
  button: {
    marginTop: spacing.xxl,
    backgroundColor: colors.primary,
    paddingVertical: spacing.lg,
    borderRadius: borderRadius.md,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textOnPrimary,
  },
});
