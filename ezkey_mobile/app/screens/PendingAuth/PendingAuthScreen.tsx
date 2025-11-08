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
import {useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PendingAuth'>;

type MockPendingAttempt = {
  authAttemptId: string;
  integrationName: string;
  tenantName: string;
  createdAt: string;
  expiresAt: string;
  challengeRequired: boolean;
  challengeHint?: string;
  challengeValue?: string;
};

const MOCK_PENDING_ATTEMPT: MockPendingAttempt = {
  authAttemptId: 'auth_987',
  integrationName: 'Acme Bank',
  tenantName: 'Retail Banking',
  createdAt: '2025-10-21T13:57:00.000Z',
  expiresAt: '2025-10-21T14:02:00.000Z',
  challengeRequired: true,
  challengeHint: 'Enter the 6-digit code shown in your admin portal.',
  challengeValue: '482913',
};

type AttemptState = 'pending' | 'accepted' | 'rejected' | 'expired';

export const PendingAuthScreen: React.FC<Props> = ({route}) => {
  const {enrollmentId} = route.params;
  const {data: enrollments} = useEnrollments();
  const [isLoading, setIsLoading] = useState(true);
  const [attempt, setAttempt] = useState<MockPendingAttempt | undefined>();
  const [state, setState] = useState<AttemptState>('pending');
  const [challengeInput, setChallengeInput] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | undefined>();
  const [isProcessing, setIsProcessing] = useState(false);

  const enrollmentMeta = useMemo(
    () => enrollments?.find(item => item.id === enrollmentId),
    [enrollmentId, enrollments],
  );

  useEffect(() => {
    const timeout = setTimeout(() => {
      setAttempt(prev => {
        const base = {
          ...MOCK_PENDING_ATTEMPT,
          integrationName: enrollmentMeta?.integrationName ?? prev?.integrationName ?? 'Integration',
          tenantName: enrollmentMeta?.tenantName ?? prev?.tenantName ?? 'Tenant',
        };
        return base;
      });
      setState('pending');
      setChallengeInput('');
      setIsLoading(false);
    }, 450);
    return () => clearTimeout(timeout);
  }, [enrollmentId, enrollmentMeta]);

  const formattedWindow = useMemo(() => {
    if (!attempt) {
      return undefined;
    }
    const created = new Date(attempt.createdAt).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
    });
    const expires = new Date(attempt.expiresAt).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
    });
    return `${created} → ${expires}`;
  }, [attempt]);

  const handleApprove = useCallback(() => {
    if (!attempt || state !== 'pending') {
      return;
    }
    if (attempt.challengeRequired) {
      if (!challengeInput.trim()) {
        setErrorMessage('Challenge code is required.');
        return;
      }
      if (attempt.challengeValue && challengeInput.trim() !== attempt.challengeValue) {
        setErrorMessage('The code does not match. Please verify the challenge.');
        return;
      }
    }
    setErrorMessage(undefined);
    setIsProcessing(true);
    setTimeout(() => {
      setState('accepted');
      setIsProcessing(false);
      Alert.alert('Authentication approved', 'Response submitted successfully (mocked).');
    }, 600);
  }, [attempt, challengeInput, state]);

  const handleReject = useCallback(() => {
    if (!attempt || state !== 'pending') {
      return;
    }
    setErrorMessage(undefined);
    setIsProcessing(true);
    setTimeout(() => {
      setState('rejected');
      setIsProcessing(false);
      Alert.alert('Authentication rejected', 'The request was denied (mocked).');
    }, 600);
  }, [attempt, state]);

  const handleRefresh = useCallback(() => {
    setIsLoading(true);
    setAttempt(undefined);
    setTimeout(() => {
      setAttempt({
        ...MOCK_PENDING_ATTEMPT,
        integrationName: enrollmentMeta?.integrationName ?? MOCK_PENDING_ATTEMPT.integrationName,
        tenantName: enrollmentMeta?.tenantName ?? MOCK_PENDING_ATTEMPT.tenantName,
      });
      setState('pending');
      setChallengeInput('');
      setIsLoading(false);
    }, 450);
  }, [enrollmentMeta]);

  const showEmptyState = !isLoading && !attempt;

  return (
    <View style={styles.container}>
      <Text style={styles.heading}>Pending authentication</Text>
      <Text style={styles.subtitle}>
        Enrollment ID <Text style={styles.emphasis}>{enrollmentId}</Text>
      </Text>
      {isLoading ? (
        <View style={styles.loading}>
          <ActivityIndicator />
          <Text style={styles.loadingText}>Contacting Ezkey Auth API…</Text>
        </View>
      ) : showEmptyState ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>No pending requests</Text>
          <Text style={styles.emptyBody}>
            Pull to refresh or wait for a new authentication attempt to arrive.
          </Text>
          <TouchableOpacity onPress={handleRefresh} style={styles.secondaryButton}>
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
                {attempt.challengeHint ? (
                  <Text style={styles.challengeHint}>{attempt.challengeHint}</Text>
                ) : null}
                <TextInput
                  value={challengeInput}
                  onChangeText={text => {
                    setChallengeInput(text.replace(/[^0-9]/g, ''));
                    setErrorMessage(undefined);
                  }}
                  placeholder="Enter code"
                  keyboardType="number-pad"
                  maxLength={6}
                  style={styles.challengeInput}
                  placeholderTextColor="#5f6780"
                  editable={state === 'pending' && !isProcessing}
                />
              </View>
            ) : null}
            {errorMessage ? <Text style={styles.errorText}>{errorMessage}</Text> : null}
            {state === 'pending' ? (
              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={handleReject}
                  style={[styles.actionButton, styles.rejectButton]}
                  disabled={isProcessing}>
                  <Text style={styles.rejectLabel}>Deny</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={handleApprove}
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
                <TouchableOpacity onPress={handleRefresh} style={styles.secondaryButton}>
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
  errorText: {
    fontSize: 13,
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
});
