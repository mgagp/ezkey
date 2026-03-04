/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: EnrollmentDetailScreen
 * Description: Displays enrollment metadata and routes to pending auth. Optimized for the primary action: Check pending.
 * Delete enrollment moved to Danger Zone (Manage screen).
 * @since 2025
 */

import React, {useEffect, useMemo} from 'react';
import {ActivityIndicator, Button, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {useEnrollmentStore} from '../../state/enrollmentStore';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

/**
 * Screen that surfaces enrollment metadata and routes to pending auth.
 * Primary action: Check pending. Delete moved to Danger Zone.
 *
 * @since 2025
 */
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

  const createdStr = new Date(enrollment.createdAt).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const lastStr = new Date(enrollment.lastActivityAt).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const hasCustomServer = !!enrollment.authUrl;

  return (
    <View style={styles.container}>
      <View style={styles.identityZone}>
        <View style={styles.identityRow}>
          <Text style={styles.integrationName}>{enrollment.integrationName}</Text>
          <Text style={styles.statusBadge}>{enrollment.status.toUpperCase()}</Text>
        </View>
        <Text style={styles.tenantLine}>{enrollment.tenantName}</Text>
        {enrollment.enrollmentName ? (
          <Text style={styles.deviceLine}>{enrollment.enrollmentName}</Text>
        ) : null}
      </View>

      <View style={styles.metaZone}>
        <Text style={styles.metaLine}>
          Created {createdStr} · Last {lastStr}
        </Text>
      </View>

      {hasCustomServer ? (
        <View style={styles.serverZone}>
          <Text style={styles.serverLabel}>Server</Text>
          <Text style={styles.serverValue}>{enrollment.authUrl}</Text>
        </View>
      ) : null}

      <TouchableOpacity style={styles.primaryButton} onPress={navigateToPending}>
        <Text style={styles.primaryLabel}>Check pending</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    gap: 16,
    backgroundColor: '#0b0d11',
  },
  identityZone: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.15)',
  },
  identityRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  integrationName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  statusBadge: {
    fontSize: 11,
    fontWeight: '700',
    color: '#61d095',
    letterSpacing: 0.5,
  },
  tenantLine: {
    fontSize: 14,
    color: '#9aa3b6',
    marginTop: 6,
  },
  deviceLine: {
    fontSize: 13,
    color: '#c2c8d5',
    marginTop: 2,
  },
  metaZone: {
    paddingHorizontal: 4,
  },
  metaLine: {
    fontSize: 12,
    color: '#9aa3b6',
  },
  serverZone: {
    backgroundColor: '#0f1628',
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: 'rgba(54, 115, 223, 0.2)',
  },
  serverLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: '#5a7aa8',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 4,
  },
  serverValue: {
    fontSize: 12,
    color: '#5a9cf7',
  },
  primaryButton: {
    backgroundColor: '#3076df',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  primaryLabel: {
    fontSize: 17,
    fontWeight: '600',
    color: '#ffffff',
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
