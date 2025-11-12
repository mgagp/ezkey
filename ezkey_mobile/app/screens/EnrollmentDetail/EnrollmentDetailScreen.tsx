/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentDetailScreen
 * Description: Displays enrollment metadata and supports secure deletion flows.
 * Security Context: Reinforces lifecycle expectations from docs/features/AUTH_SECURITY.md by revoking device keys when
 *                   enrollments are removed and highlighting the binding between device alias and proof tokens.
 * @since 2025
 */

import React, {useCallback, useEffect, useMemo} from 'react';
import {ActivityIndicator, Alert, Button, StyleSheet, Text, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useDeleteEnrollment, useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {cryptoService} from '../../services/crypto';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

/**
 * Screen that surfaces enrollment metadata and allows users to manage their device bindings.
 *
 * @param route Navigation route containing the requested enrollment identifier.
 * @param navigation Navigation helpers for stack transitions.
 * @since 2025
 */
export const EnrollmentDetailScreen: React.FC<Props> = ({route, navigation}) => {
  const {enrollmentId} = route.params;
  const {data, isLoading} = useEnrollments();
  const deleteEnrollment = useDeleteEnrollment();
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

  const confirmDelete = useCallback(() => {
    if (!enrollment || deleteEnrollment.isPending) {
      return;
    }
    Alert.alert(
      'Delete enrollment',
      `Are you sure you want to delete the enrollment for ${enrollment.integrationName}? This will remove the stored keys on this device.`,
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              await deleteEnrollment.mutateAsync(enrollment.id);
              await cryptoService.deleteKey(enrollment.deviceAlias);
              navigation.popToTop();
            } catch (error) {
              console.error('[EnrollmentDetail] Failed to delete enrollment', error);
              Alert.alert('Deletion failed', 'Unable to delete the enrollment. Please try again.');
            }
          },
        },
      ],
    );
  }, [deleteEnrollment, enrollment, navigation]);

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
      <Button
        title={deleteEnrollment.isPending ? 'Deleting…' : 'Delete enrollment'}
        onPress={confirmDelete}
        color="#ff6666"
        disabled={deleteEnrollment.isPending}
      />
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
