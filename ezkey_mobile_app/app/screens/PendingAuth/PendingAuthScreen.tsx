/**
 * Pending auth: Approve/Deny only. No mobile cancel API — navigating away relies on server TTL expiry.
 */
import React, {useCallback, useEffect, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import axios from 'axios';
import {RootStackParamList} from '../../navigation/types';
import {colors, spacing, typography, borderRadius} from '../../config/theme';
import {ChallengeInput} from '../../components/ChallengeInput';
import {useEnrollmentById} from '../../hooks/useEnrollments';
import {authAttemptsApi} from '../../services/api/authAttempts';
import {buildPendingPayload, buildRespondPayload} from '../../services/crypto/authAttemptPayload';
import {getCryptoService} from '../../services/crypto/cryptoService';

type Props = StackScreenProps<RootStackParamList, 'PendingAuth'>;

type ResultState = 'idle' | 'approved' | 'rejected' | 'failed';

type PendingAttempt = {
  authAttemptId: string | number;
  authAttemptProofToken: string;
  authAttemptChallengeRequired: boolean;
  contextTitle?: string;
  contextMessage?: string;
  integrationName: string;
};

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const msg =
      error.response?.data?.message ??
      error.response?.data?.error ??
      error.message;
    return String(msg);
  }
  if (error instanceof Error) return error.message;
  return String(error);
}

export const PendingAuthScreen: React.FC<Props> = ({route, navigation}) => {
  const insets = useSafeAreaInsets();
  const {enrollmentId} = route.params;
  const {data: enrollment, isLoading: isEnrollmentLoading} = useEnrollmentById(enrollmentId);

  const [loading, setLoading] = useState(false);
  const [attempt, setAttempt] = useState<PendingAttempt | null>(null);
  const [noPending, setNoPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [twoDigit, setTwoDigit] = useState('');
  const [result, setResult] = useState<ResultState>('idle');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadPending = useCallback(async () => {
    if (!enrollment) return;
    setLoading(true);
    setError(null);
    setAttempt(null);
    setNoPending(false);
    try {
      const crypto = getCryptoService();
      const eid = enrollment.id.toString();
      await crypto.ensureEnrollmentKeyPair(eid);
      const deviceProofToken = Date.now().toString();
      const deviceProofTokenSigned = await crypto.sign(eid, deviceProofToken);
      const response = await authAttemptsApi.pending(
        {
          enrollmentId: enrollment.id,
          enrollmentProofToken: enrollment.enrollmentProofToken,
          deviceProofToken,
          deviceProofTokenSigned,
        },
        enrollment.authUrl,
      );
      if (!response?.authAttemptId) {
        setNoPending(true);
        return;
      }
      const integrationPublicKey = enrollment.integrationPublicKey;
      if (!integrationPublicKey) {
        setError('Enrollment missing integration public key; cannot verify pending response.');
        return;
      }
      const pendingPayload = buildPendingPayload(
        response.authAttemptProofToken,
        response.authAttemptChallengeRequired ?? false,
        response.contextTitle,
        response.contextMessage,
      );
      const signatureValid = await crypto.verify(
        pendingPayload,
        response.authAttemptProofTokenSignedByIntegration,
        integrationPublicKey,
      );
      if (!signatureValid) {
        setError('Invalid integration signature on pending response.');
        return;
      }
      setAttempt({
        authAttemptId: response.authAttemptId,
        authAttemptProofToken: response.authAttemptProofToken,
        authAttemptChallengeRequired: response.authAttemptChallengeRequired ?? false,
        contextTitle: response.contextTitle,
        contextMessage: response.contextMessage,
        integrationName: enrollment.integrationName,
      });
      setTwoDigit('');
    } catch (e) {
      if (axios.isAxiosError(e) && e.response?.status === 404) {
        setNoPending(true);
      } else {
        setError(extractErrorMessage(e));
      }
    } finally {
      setLoading(false);
    }
  }, [enrollment]);

  useEffect(() => {
    if (!isEnrollmentLoading && enrollment) {
      loadPending();
    }
  }, [enrollment, isEnrollmentLoading, loadPending]);

  const requiresChallenge = attempt?.authAttemptChallengeRequired ?? false;
  const canApprove = !requiresChallenge || twoDigit.length === 2;

  const handleRespond = useCallback(
    async (accepted: boolean) => {
      if (!enrollment || !attempt) return;
      if (accepted && requiresChallenge && twoDigit.length !== 2) {
        setError('Enter the 2-digit code from the admin console.');
        return;
      }
      setError(null);
      setIsSubmitting(true);
      try {
        const crypto = getCryptoService();
        const eid = enrollment.id.toString();
        await crypto.ensureEnrollmentKeyPair(eid);
        const respondPayload = buildRespondPayload(attempt.authAttemptProofToken, accepted);
        const proofTokenSigned = await crypto.sign(eid, respondPayload);
        const response = await authAttemptsApi.respond(
          {
            authAttemptId: attempt.authAttemptId,
            authAttemptAccepted: accepted,
            authAttemptProofTokenSignedByDevice: proofTokenSigned,
            authAttemptChallengeResponse: twoDigit.trim() || undefined,
          },
          enrollment.authUrl,
        );
        if (response?.result === 'APPROVED') {
          setResult('approved');
        } else if (response?.result === 'DENIED' || response?.result === 'REJECTED') {
          setResult('rejected');
        } else {
          setResult('failed');
          setError(response?.message ?? 'Request could not be completed.');
        }
      } catch (e) {
        setError(extractErrorMessage(e));
      } finally {
        setIsSubmitting(false);
      }
    },
    [enrollment, attempt, requiresChallenge, twoDigit],
  );

  const onCheckAgain = () => {
    setResult('idle');
    setTwoDigit('');
    loadPending();
  };

  const goBack = () => navigation.goBack();

  if (isEnrollmentLoading || !enrollment) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  if (result === 'approved') {
    return (
      <View style={[styles.container, {paddingBottom: insets.bottom}]}>
        <View style={[styles.resultCard, styles.resultSuccess]}>
          <Text style={styles.resultTitle}>Approved</Text>
          <Text style={styles.resultText}>
            The sign-in request was approved successfully.
          </Text>
          <TouchableOpacity style={styles.secondaryButton} onPress={onCheckAgain}>
            <Text style={styles.secondaryButtonText}>Check again</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.secondaryButton} onPress={goBack}>
            <Text style={styles.secondaryButtonText}>Back to enrollment</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  if (result === 'rejected') {
    return (
      <View style={[styles.container, {paddingBottom: insets.bottom}]}>
        <View style={[styles.resultCard, styles.resultError]}>
          <Text style={styles.resultTitle}>Rejected</Text>
          <Text style={styles.resultText}>The sign-in request was denied.</Text>
          <TouchableOpacity style={styles.secondaryButton} onPress={onCheckAgain}>
            <Text style={styles.secondaryButtonText}>Check again</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.secondaryButton} onPress={goBack}>
            <Text style={styles.secondaryButtonText}>Back to enrollment</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Checking for pending request…</Text>
      </View>
    );
  }

  if (noPending || !attempt) {
    return (
      <View style={[styles.container, styles.centered, {padding: spacing.xl}]}>
        <Text style={styles.emptyTitle}>No pending request</Text>
        <Text style={styles.emptySubtitle}>
          There is no sign-in request waiting for this enrollment.
        </Text>
        <TouchableOpacity style={styles.checkAgainButton} onPress={loadPending}>
          <Text style={styles.secondaryButtonText}>Check again</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.secondaryButton} onPress={goBack}>
          <Text style={styles.secondaryButtonText}>Back to enrollment</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (error && result === 'failed') {
    return (
      <View style={[styles.container, {padding: spacing.xl}]}>
        <View style={styles.errorBanner}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
        <TouchableOpacity style={styles.secondaryButton} onPress={onCheckAgain}>
          <Text style={styles.secondaryButtonText}>Check again</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.secondaryButton} onPress={goBack}>
          <Text style={styles.secondaryButtonText}>Back to enrollment</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      keyboardShouldPersistTaps="handled">
      {error ? (
        <View style={styles.errorBanner}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>{attempt.integrationName}</Text>
        {attempt.contextTitle != null && attempt.contextTitle !== '' && (
          <Text style={styles.contextTitle}>{attempt.contextTitle}</Text>
        )}
        {attempt.contextMessage != null && attempt.contextMessage !== '' && (
          <Text style={styles.contextMessage}>{attempt.contextMessage}</Text>
        )}
      </View>

      {requiresChallenge && (
        <>
          <Text style={styles.challengeLabel}>Enter 2-digit code</Text>
          <ChallengeInput
            value={twoDigit}
            onChange={setTwoDigit}
            digitCount={2}
            accessibilityLabel="Approval challenge code"
          />
        </>
      )}

      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.button, styles.approveButton, (!canApprove || isSubmitting) && styles.buttonDisabled]}
          onPress={() => handleRespond(true)}
          disabled={!canApprove || isSubmitting}
          activeOpacity={0.85}>
          <Text style={styles.buttonText}>
            {isSubmitting ? 'Submitting…' : 'Approve'}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, styles.denyButton]}
          onPress={() => handleRespond(false)}
          disabled={isSubmitting}
          activeOpacity={0.85}>
          <Text style={styles.denyButtonText}>Deny</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
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
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: spacing.md,
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
  },
  emptyTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  emptySubtitle: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
    textAlign: 'center',
    marginBottom: spacing.xl,
  },
  checkAgainButton: {
    paddingVertical: spacing.md,
    marginBottom: spacing.sm,
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
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  contextTitle: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
  },
  contextMessage: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginBottom: spacing.sm,
  },
  challengeLabel: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.md,
  },
  actions: {
    gap: spacing.md,
    marginTop: spacing.xl,
  },
  button: {
    paddingVertical: spacing.lg,
    borderRadius: borderRadius.md,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  approveButton: {
    backgroundColor: colors.primary,
  },
  buttonText: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textOnPrimary,
  },
  denyButton: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderStrong,
  },
  denyButtonText: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.error,
  },
  resultCard: {
    margin: spacing.xl,
    padding: spacing.xxl,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
  },
  resultSuccess: {
    backgroundColor: colors.surface,
    borderColor: colors.success,
  },
  resultError: {
    backgroundColor: colors.surface,
    borderColor: colors.error,
  },
  resultTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.bold,
    color: colors.textPrimary,
    marginBottom: spacing.md,
  },
  resultText: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.xl,
  },
  secondaryButton: {
    paddingVertical: spacing.md,
    marginTop: spacing.sm,
  },
  secondaryButtonText: {
    fontSize: typography.fontSize.base,
    color: colors.primaryLight,
    fontWeight: typography.fontWeight.medium,
  },
});
