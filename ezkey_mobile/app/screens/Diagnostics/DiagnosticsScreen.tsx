import React, {useMemo, useState} from 'react';
import {Buffer} from 'buffer';
import {
  ActivityIndicator,
  FlatList,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useEnrollments} from '../../hooks/useEnrollments';
import {useSecureEnrollmentInfo} from '../../hooks/useSecureEnrollmentInfo';
import {cryptoService} from '../../services/crypto';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';

type DiagnosticState = {
  publicKey?: string;
  signature?: string;
  error?: string;
  timestamp?: string;
};

const TEST_PHRASE = 'ezkey-mobile-diagnostic';

export const DiagnosticsScreen: React.FC = () => {
  const {data: enrollments, isLoading} = useEnrollments();

  const providerLabel = useMemo(
    () =>
      cryptoService.activeProvider === 'native'
        ? 'Native Keystore/Keychain'
        : 'Mock (fallback)',
    [],
  );

  return (
    <View style={styles.screen}>
      <View style={styles.banner}>
        <Text style={styles.bannerTitle}>Crypto diagnostics</Text>
        <Text style={styles.bannerMeta}>Provider: {providerLabel}</Text>
        <Text style={styles.bannerHelp}>
          Run a self-test to generate a diagnostic signature using the stored device key. Share the
          signature and public key with the backend to validate.
        </Text>
      </View>
      {isLoading ? (
        <View style={styles.loader}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={enrollments}
          keyExtractor={item => item.id}
          renderItem={({item}) => <DiagnosticCard enrollment={item} />}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Text style={styles.emptyText}>No enrollments available for diagnostics.</Text>
            </View>
          }
        />
      )}
    </View>
  );
};

const DiagnosticCard: React.FC<{enrollment: StoredEnrollment}> = ({enrollment}) => {
  const {data: secureInfo, isLoading: secureLoading} = useSecureEnrollmentInfo(enrollment.id);
  const [result, setResult] = useState<DiagnosticState>();

  const alias = secureInfo?.deviceAlias;

  const runSelfTest = async () => {
    if (!alias) {
      setResult({
        error: 'No device alias available for this enrollment.',
        timestamp: new Date().toISOString(),
      });
      return;
    }
    setResult({timestamp: new Date().toISOString()});
    try {
      const publicKey = await cryptoService.ensureKeyPair(alias);
      const payload = Buffer.from(`${TEST_PHRASE}:${Date.now()}`, 'utf-8').toString('base64');
      const signature = await cryptoService.sign(alias, payload);
      setResult({
        publicKey,
        signature,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      setResult({
        error: error instanceof Error ? error.message : String(error),
        timestamp: new Date().toISOString(),
      });
    }
  };

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <Text style={styles.title}>{enrollment.integrationName}</Text>
        <Text style={styles.status}>{enrollment.status.toUpperCase()}</Text>
      </View>
      <Text style={styles.subtitle}>{enrollment.enrollmentName ?? 'Device enrollment'}</Text>
      <Text style={styles.meta}>Enrollment ID: {enrollment.id}</Text>
      <Text style={styles.meta}>Tenant: {enrollment.tenantName}</Text>
      <Text style={styles.meta}>Alias: {alias ?? '—'}</Text>
      {secureLoading ? (
        <View style={styles.inlineLoader}>
          <ActivityIndicator size="small" />
          <Text style={styles.meta}>Resolving secure storage…</Text>
        </View>
      ) : !alias ? (
        <Text style={styles.error}>
          No secure alias stored. Re-enroll the device or run recovery to restore keys.
        </Text>
      ) : null}
      {result?.timestamp ? (
        <Text style={styles.meta}>
          Last test: {new Date(result.timestamp).toLocaleTimeString(undefined, {hour12: false})}
        </Text>
      ) : null}
      {result?.error ? <Text style={styles.error}>Error: {result.error}</Text> : null}
      {result?.publicKey ? (
        <ScrollView horizontal contentContainerStyle={styles.scrollRow}>
          <Text style={styles.monoLabel}>Public key:</Text>
          <Text style={styles.monoValue}>{truncate(result.publicKey)}</Text>
        </ScrollView>
      ) : null}
      {result?.signature ? (
        <ScrollView horizontal contentContainerStyle={styles.scrollRow}>
          <Text style={styles.monoLabel}>Signature:</Text>
          <Text style={styles.monoValue}>{truncate(result.signature)}</Text>
        </ScrollView>
      ) : null}
      <TouchableOpacity
        style={[styles.button, !alias ? styles.buttonDisabled : undefined]}
        onPress={runSelfTest}
        disabled={!alias}>
        <Text style={styles.buttonLabel}>{alias ? 'Run self-test' : 'Alias missing'}</Text>
      </TouchableOpacity>
    </View>
  );
};

const truncate = (value: string | undefined, max = 96) =>
  value && value.length > max ? `${value.slice(0, max)}…` : value ?? '';

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0b0d11',
  },
  banner: {
    padding: 16,
    gap: 8,
    backgroundColor: '#151923',
  },
  bannerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  bannerMeta: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  bannerHelp: {
    fontSize: 13,
    color: '#c2c8d5',
  },
  loader: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listContent: {
    padding: 16,
    gap: 16,
  },
  card: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    gap: 8,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  status: {
    fontSize: 12,
    fontWeight: '700',
    color: '#61d095',
  },
  subtitle: {
    fontSize: 14,
    color: '#c2c8d5',
  },
  meta: {
    fontSize: 12,
    color: '#9aa3b6',
  },
  error: {
    fontSize: 12,
    color: '#ff6666',
  },
  inlineLoader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  scrollRow: {
    paddingVertical: 4,
    gap: 8,
  },
  monoLabel: {
    fontSize: 12,
    color: '#9aa3b6',
    fontFamily: 'Courier',
  },
  monoValue: {
    fontSize: 12,
    color: '#f4f7ff',
    fontFamily: 'Courier',
  },
  button: {
    marginTop: 8,
    backgroundColor: '#61d095',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.4,
  },
  buttonLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#0b0d11',
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  emptyText: {
    fontSize: 14,
    color: '#9aa3b6',
  },
});

