import React, {useEffect, useState} from 'react';
import {Alert} from 'react-native';
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
import {cryptoService, nativeCrypto} from '../../services/crypto';
import {enrollmentStorage, StoredEnrollment} from '../../services/storage/enrollmentStorage';

type DiagnosticState = {
  publicKey?: string;
  signature?: string;
  error?: string;
  timestamp?: string;
};

const TEST_PHRASE = 'ezkey-mobile-diagnostic';

export const DiagnosticsScreen: React.FC = () => {
  const {data: enrollments, isLoading, refetch} = useEnrollments();
  const [nativeBuildTimestampUtc, setNativeBuildTimestampUtc] = useState<string | undefined>();

  useEffect(() => {
    let cancelled = false;
    void nativeCrypto
      .getBuildTimestamp()
      .then(value => {
        if (!cancelled) {
          setNativeBuildTimestampUtc(value.trim().length > 0 ? value : undefined);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setNativeBuildTimestampUtc(undefined);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleClearAllData = async () => {
    Alert.alert(
      'Clear All Data',
      'This will delete all enrollments and reset the app. This action cannot be undone.',
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Clear All',
          style: 'destructive',
          onPress: async () => {
            try {
              await enrollmentStorage.clearAll();
              await refetch();
            } catch (error) {
              Alert.alert(
                'Error',
                `Failed to clear data: ${error instanceof Error ? error.message : String(error)}`,
              );
            }
          },
        },
      ],
    );
  };

  return (
    <View style={styles.screen}>
      <View style={styles.banner}>
        <Text style={styles.bannerTitle}>Crypto diagnostics</Text>
        {nativeBuildTimestampUtc ? (
          <Text style={styles.bannerMeta} selectable>
            Native build (UTC): {nativeBuildTimestampUtc}
          </Text>
        ) : (
          <Text style={styles.bannerMeta}>Native build (UTC): unavailable</Text>
        )}
        <Text style={styles.bannerMeta}>Provider: Native Keystore/Keychain</Text>
        <Text style={styles.bannerHelp}>
          Run a self-test to generate a diagnostic signature using the stored device key. Share the
          signature and public key with the backend to validate.
        </Text>
        <TouchableOpacity style={styles.clearButton} onPress={handleClearAllData}>
          <Text style={styles.clearButtonLabel}>Clear All Enrollment Data</Text>
        </TouchableOpacity>
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
  const [result, setResult] = useState<DiagnosticState>();

  const enrollmentId = enrollment.id.toString();

  const runSelfTest = async () => {
    setResult({timestamp: new Date().toISOString()});
    try {
      // Ensure EC P-256 key pair exists for this enrollment
      await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
      // Get EC P-256 public key for this enrollment
      const publicKey = await cryptoService.getPublicKey(enrollmentId);
      const payload = `${TEST_PHRASE}:${Date.now()}`;
      const signature = await cryptoService.sign(enrollmentId, payload);
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
      <TouchableOpacity style={styles.button} onPress={runSelfTest}>
        <Text style={styles.buttonLabel}>Run self-test</Text>
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
  clearButton: {
    marginTop: 12,
    backgroundColor: '#ff6666',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 16,
    alignItems: 'center',
  },
  clearButtonLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#ffffff',
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

