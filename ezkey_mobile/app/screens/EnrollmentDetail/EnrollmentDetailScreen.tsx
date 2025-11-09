import React, {useEffect, useMemo} from 'react';
import {ActivityIndicator, Button, StyleSheet, Text, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentStore} from '../../state/enrollmentStore';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

export const EnrollmentDetailScreen: React.FC<Props> = ({route, navigation}) => {
  const {enrollmentId} = route.params;
  const {data, isLoading} = useEnrollments();
  const selectedId = useEnrollmentStore(store => store.selectedId);
  const targetId = enrollmentId ?? selectedId;

  const enrollment = useMemo(() => {
    if (!targetId || !data) {
      return undefined;
    }
    return data.find(item => item.id === targetId);
  }, [data, targetId]);

  useEffect(() => {
    if (enrollment) {
      navigation.setOptions({title: enrollment.integrationName});
    }
  }, [enrollment, navigation]);

  const navigateToPending = () => {
    navigation.navigate('PendingAuth', {enrollmentId});
  };

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator />
      </View>
    );
  }

  if (!enrollment) {
    return (
      <View style={styles.missingContainer}>
        <Text style={styles.missingText}>
          Unable to locate the selected enrollment. Return to Home and try again.
        </Text>
        <Button title="Back to Home" onPress={() => navigation.popToTop()} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.label}>Integration</Text>
        <Text style={styles.value}>{enrollment.integrationName}</Text>

        <Text style={styles.label}>Tenant</Text>
        <Text style={styles.value}>{enrollment.tenantName}</Text>

        {enrollment.enrollmentName ? (
          <>
            <Text style={styles.label}>Device label</Text>
            <Text style={styles.value}>{enrollment.enrollmentName}</Text>
          </>
        ) : null}

        <Text style={styles.label}>Enrollment state</Text>
        <Text style={styles.value}>{enrollment.status.toUpperCase()}</Text>

        <Text style={styles.label}>Created</Text>
        <Text style={styles.value}>
          {new Date(enrollment.createdAt).toLocaleString(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short',
          })}
        </Text>

        <Text style={styles.label}>Last activity</Text>
        <Text style={styles.value}>
          {new Date(enrollment.lastActivityAt).toLocaleString(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short',
          })}
        </Text>
      </View>

      <Button title="Check pending" onPress={navigateToPending} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 24,
    backgroundColor: '#0b0d11',
  },
  card: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 20,
    gap: 12,
  },
  label: {
    fontSize: 12,
    color: '#9aa3b6',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  value: {
    fontSize: 16,
    color: '#f4f7ff',
    fontWeight: '500',
  },
  missingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 16,
    backgroundColor: '#0b0d11',
  },
  missingText: {
    fontSize: 16,
    color: '#f4f7ff',
    textAlign: 'center',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0b0d11',
  },
});
