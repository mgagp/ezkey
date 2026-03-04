/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: DangerZoneScreen
 * Description: Administrative screen for destructive actions (delete enrollments).
 * Keeps dangerous operations out of the main enrollment flow.
 * @since 2025
 */

import React, {useCallback} from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {useDeleteEnrollment, useEnrollments} from '../../hooks/useEnrollments';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';

/**
 * Administrative screen for deleting enrollments.
 * Separated from the main flow to avoid accidental deletion.
 *
 * @since 2025
 */
export const DangerZoneScreen: React.FC = () => {
  const navigation = useNavigation();
  const {data: enrollments, isLoading} = useEnrollments();
  const deleteMutation = useDeleteEnrollment();

  const handleDelete = useCallback(
    (enrollment: StoredEnrollment) => {
      Alert.alert(
        'Delete enrollment',
        `Remove the enrollment for ${enrollment.integrationName}? This will unlink this device.`,
        [
          {text: 'Cancel', style: 'cancel'},
          {
            text: 'Delete',
            style: 'destructive',
            onPress: async () => {
              try {
                await deleteMutation.mutateAsync(enrollment.id);
                navigation.goBack();
              } catch (error) {
                console.error('[DangerZone] Failed to delete', error);
                Alert.alert('Deletion failed', 'Unable to delete. Please try again.');
              }
            },
          },
        ],
      );
    },
    [deleteMutation, navigation],
  );

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.banner}>
        <Text style={styles.bannerTitle}>Danger Zone</Text>
        <Text style={styles.bannerHint}>
          Delete enrollments to unlink this device. This action cannot be undone.
        </Text>
      </View>
      <FlatList
        data={enrollments ?? []}
        keyExtractor={item => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyText}>No enrollments to manage.</Text>
          </View>
        }
        renderItem={({item}) => (
          <View style={styles.row}>
            <View style={styles.rowInfo}>
              <Text style={styles.rowTitle}>{item.integrationName}</Text>
              <Text style={styles.rowMeta}>{item.tenantName}</Text>
            </View>
            <TouchableOpacity
              style={[styles.deleteButton, deleteMutation.isPending && styles.deleteButtonDisabled]}
              onPress={() => handleDelete(item)}
              disabled={deleteMutation.isPending}>
              <Text style={styles.deleteLabel}>Delete</Text>
            </TouchableOpacity>
          </View>
        )}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0b0d11',
  },
  banner: {
    padding: 16,
    backgroundColor: 'rgba(255, 102, 102, 0.1)',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 102, 102, 0.2)',
  },
  bannerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#ff7878',
  },
  bannerHint: {
    fontSize: 13,
    color: '#9aa3b6',
    marginTop: 6,
    lineHeight: 20,
  },
  listContent: {
    padding: 16,
    gap: 12,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
  },
  rowInfo: {
    flex: 1,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  rowMeta: {
    fontSize: 13,
    color: '#9aa3b6',
    marginTop: 2,
  },
  deleteButton: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#ff6666',
  },
  deleteButtonDisabled: {
    opacity: 0.5,
  },
  deleteLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#ffffff',
  },
  empty: {
    padding: 32,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 14,
    color: '#9aa3b6',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0b0d11',
  },
});
