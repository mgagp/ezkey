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

import React, {useCallback, useMemo, useState} from 'react';
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
import {shouldShowInstallationHostHint} from '../../utils/installationMetadata';

const RECENT_ACTIVITY_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000;

const sortEnrollments = (items: StoredEnrollment[]) =>
  [...items].sort((left, right) => {
    if (left.favorited && !right.favorited) {
      return -1;
    }
    if (!left.favorited && right.favorited) {
      return 1;
    }
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
  });

const getEnrollmentDisplayName = (enrollment: StoredEnrollment) =>
  enrollment.enrollmentName?.trim() ||
  enrollment.deviceLabel?.trim() ||
  enrollment.integrationName;

const formatAbsoluteDateTime = (value?: string) => {
  if (!value) {
    return undefined;
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }

  return parsed.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
};

const formatRelativeAge = (value?: string, now: number = Date.now()) => {
  if (!value) {
    return undefined;
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }

  const diffMs = Math.max(0, now - parsed.getTime());
  const diffDays = Math.floor(diffMs / (24 * 60 * 60 * 1000));

  if (diffDays <= 0) {
    return 'today';
  }
  if (diffDays === 1) {
    return '1 day ago';
  }
  if (diffDays < 7) {
    return `${diffDays} days ago`;
  }

  const diffWeeks = Math.floor(diffDays / 7);
  if (diffWeeks === 1) {
    return '1 week ago';
  }
  if (diffWeeks < 5) {
    return `${diffWeeks} weeks ago`;
  }

  const diffMonths = Math.floor(diffDays / 30);
  if (diffMonths === 1) {
    return '1 month ago';
  }

  return `${diffMonths} months ago`;
};

const buildRecencyLabel = (enrollment: StoredEnrollment, now: number = Date.now()) => {
  const lastActivity = formatRelativeAge(enrollment.lastActivityAt, now);
  if (lastActivity) {
    return `Last active ${lastActivity}`;
  }

  const created = formatRelativeAge(enrollment.createdAt, now);
  if (created) {
    return `Enrolled ${created}`;
  }

  return 'Enrollment date unavailable';
};

const wasRecentlyActive = (enrollment: StoredEnrollment, now: number = Date.now()) => {
  const lastActivityAt = Date.parse(enrollment.lastActivityAt);
  if (Number.isNaN(lastActivityAt)) {
    return false;
  }

  return now - lastActivityAt <= RECENT_ACTIVITY_THRESHOLD_MS;
};

const buildDeleteMessage = (enrollment: StoredEnrollment) => {
  const lines = [
    `Remove "${getEnrollmentDisplayName(enrollment)}"?`,
    '',
    `Integration: ${enrollment.integrationName}`,
  ];

  if (enrollment.tenantName) {
    lines.push(`Tenant: ${enrollment.tenantName}`);
  }

  const installationLabel = enrollment.installation?.name || enrollment.installation?.host;
  if (installationLabel) {
    lines.push(`Installation: ${installationLabel}`);
  }

  const lastActivity = formatAbsoluteDateTime(enrollment.lastActivityAt);
  if (lastActivity) {
    lines.push(`Last activity: ${lastActivity}`);
  }

  if (enrollment.favorited) {
    lines.push('Marked as favorite on this device.');
  }

  if (wasRecentlyActive(enrollment)) {
    lines.push('Used recently on this device.');
  }

  lines.push('', 'This will unlink this device and cannot be undone.');
  return lines.join('\n');
};

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
  const sortedEnrollments = useMemo(
    () => sortEnrollments(enrollments ?? []),
    [enrollments],
  );

  const handleDelete = useCallback(
    (enrollment: StoredEnrollment) => {
      Alert.alert(
        'Delete enrollment',
        buildDeleteMessage(enrollment),
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
      <View style={styles.loadingContainer} accessibilityLabel="Loading danger zone">
        <ActivityIndicator color={colors.primaryLight} accessibilityLabel="Loading" />
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
        data={sortedEnrollments}
        keyExtractor={item => item.id}
        accessibilityLabel="Enrollments that can be deleted"
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
              disabled={clearAllPending}
              accessibilityRole="button"
              accessibilityLabel="Clear all enrollment data"
              accessibilityHint="Removes every enrollment from this device">
              <Text style={styles.clearAllLabel}>Clear all enrollment data</Text>
            </TouchableOpacity>
            <Text style={styles.footerHint}>
              Removes every enrollment from this device at once. Use individual delete above when you only need to
              remove one.
            </Text>
          </View>
        }
        renderItem={({item}) => {
          const displayName = getEnrollmentDisplayName(item);
          const showHostHint = shouldShowInstallationHostHint(item);
          const installationLabel = showHostHint && item.installation?.host
            ? `${item.installation?.name ?? 'Ezkey installation'} · ${item.installation.host}`
            : item.installation?.name;

          return (
            <View style={styles.row}>
              <View style={styles.rowInfo}>
                <View style={styles.rowTitleLine}>
                  <Text style={styles.rowTitle}>{displayName}</Text>
                  {item.favorited ? <Text style={styles.favoriteBadge}>Favorite</Text> : null}
                </View>
                <Text style={styles.rowMeta}>{item.integrationName}</Text>
                <Text style={styles.rowMeta}>
                  {[item.tenantName, installationLabel].filter(Boolean).join(' · ')}
                </Text>
                <Text
                  style={[
                    styles.rowRecency,
                    wasRecentlyActive(item) && styles.rowRecencyRecent,
                  ]}>
                  {buildRecencyLabel(item)}
                </Text>
              </View>
              <TouchableOpacity
                style={[styles.deleteButton, deleteMutation.isPending && styles.deleteButtonDisabled]}
                onPress={() => handleDelete(item)}
                disabled={deleteMutation.isPending}
                accessibilityRole="button"
                accessibilityLabel={`Delete enrollment ${displayName}`}
                accessibilityHint="Unlinks this device from this enrollment">
                <Text style={styles.deleteLabel}>Delete</Text>
              </TouchableOpacity>
            </View>
          );
        }}
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
    paddingRight: spacing.md,
  },
  rowTitleLine: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  rowTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    flexShrink: 1,
  },
  rowMeta: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: 2,
  },
  rowRecency: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.sm,
  },
  rowRecencyRecent: {
    color: colors.errorLight,
  },
  favoriteBadge: {
    fontSize: typography.fontSize.xs,
    fontWeight: typography.fontWeight.semibold,
    color: colors.errorLight,
    backgroundColor: 'rgba(255, 102, 102, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255, 102, 102, 0.25)',
    borderRadius: borderRadius.xl,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
  },
  deleteButton: {
    paddingVertical: 10,
    paddingHorizontal: spacing.lg,
    borderRadius: borderRadius.sm,
    backgroundColor: colors.error,
    alignSelf: 'flex-start',
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
