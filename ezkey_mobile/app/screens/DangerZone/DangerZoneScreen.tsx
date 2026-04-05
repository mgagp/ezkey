/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: DangerZoneScreen
 * Description: Administrative screen for destructive actions (delete enrollments, clear all data).
 * Keeps dangerous operations out of the main enrollment flow.
 * @since 2025
 */

import React, {useCallback, useState} from 'react';
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
import {enrollmentStorage, StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {borderRadius, colors, spacing, typography} from '../../config/theme';

/**
 * Administrative screen for deleting enrollments and clearing all local data.
 * Separated from the main flow to avoid accidental deletion.
 *
 * @since 2025
 */
export const DangerZoneScreen: React.FC = () => {
  const navigation = useNavigation();
  const {data: enrollments, isLoading, refetch} = useEnrollments();
  const deleteMutation = useDeleteEnrollment();
  const [clearAllPending, setClearAllPending] = useState(false);

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

  const handleClearAllData = useCallback(() => {
    Alert.alert(
      'Clear all data',
      'This will delete all enrollments and reset local enrollment data on this device. This action cannot be undone.',
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Clear all',
          style: 'destructive',
          onPress: async () => {
            setClearAllPending(true);
            try {
              await enrollmentStorage.clearAll();
              await refetch();
            } catch (error) {
              Alert.alert(
                'Error',
                `Failed to clear data: ${error instanceof Error ? error.message : String(error)}`,
              );
            } finally {
              setClearAllPending(false);
            }
          },
        },
      ],
    );
  }, [refetch]);

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator color={colors.primaryLight} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.banner}>
        <Text style={styles.bannerTitle}>Danger Zone</Text>
        <Text style={styles.bannerHint}>
          Delete enrollments to unlink this device, or clear all local enrollment data. These actions cannot be undone.
        </Text>
      </View>
      <FlatList
        data={enrollments ?? []}
        keyExtractor={item => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyText}>No enrollments to delete individually.</Text>
          </View>
        }
        ListFooterComponent={
          <View style={styles.footer}>
            <TouchableOpacity
              style={[styles.clearAllButton, clearAllPending && styles.clearAllButtonDisabled]}
              onPress={handleClearAllData}
              disabled={clearAllPending}>
              <Text style={styles.clearAllLabel}>Clear all enrollment data</Text>
            </TouchableOpacity>
            <Text style={styles.footerHint}>
              Removes every enrollment from this device at once. Use individual delete above when you only need to
              remove one.
            </Text>
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
    backgroundColor: colors.background,
  },
  banner: {
    padding: spacing.lg,
    backgroundColor: 'rgba(255, 102, 102, 0.1)',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 102, 102, 0.2)',
  },
  bannerTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.errorLight,
  },
  bannerHint: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.sm,
    lineHeight: 20,
  },
  listContent: {
    padding: spacing.lg,
    gap: spacing.md,
    paddingBottom: spacing.xxl * 2,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
  },
  rowInfo: {
    flex: 1,
  },
  rowTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  rowMeta: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: 2,
  },
  deleteButton: {
    paddingVertical: 10,
    paddingHorizontal: spacing.lg,
    borderRadius: borderRadius.sm,
    backgroundColor: colors.error,
  },
  deleteButtonDisabled: {
    opacity: 0.5,
  },
  deleteLabel: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textOnPrimary,
  },
  empty: {
    padding: spacing.xxl,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
  },
  footer: {
    marginTop: spacing.xl,
    paddingTop: spacing.lg,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  clearAllButton: {
    backgroundColor: colors.error,
    borderRadius: borderRadius.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  clearAllButtonDisabled: {
    opacity: 0.6,
  },
  clearAllLabel: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textOnPrimary,
  },
  footerHint: {
    marginTop: spacing.md,
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    lineHeight: 20,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.background,
  },
});
