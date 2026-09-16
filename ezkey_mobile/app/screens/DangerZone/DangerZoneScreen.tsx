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
import {useTranslation} from 'react-i18next';
import {useDeleteEnrollment, useEnrollments} from '../../hooks/useEnrollments';
import {
  enrollmentStorage,
  EnrollmentMetadataRecord,
} from '../../services/storage/enrollmentStorage';
import {borderRadius, colors, spacing, typography} from '../../config/theme';
import {shouldShowInstallationHostHint} from '../../utils/installationMetadata';
import {
  buildEnrollmentIdentityDisplay,
  buildHomeCardLabels,
  identityDisplayCopy,
} from '../../utils/enrollmentDisplay';

const RECENT_ACTIVITY_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000;

const sortEnrollments = (items: EnrollmentMetadataRecord[]) =>
  [...items].sort((left, right) => {
    if (left.favorited && !right.favorited) {
      return -1;
    }
    if (!left.favorited && right.favorited) {
      return 1;
    }
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
  });

const getEnrollmentDisplayName = (
  enrollment: EnrollmentMetadataRecord,
  t: (key: string) => string,
) => {
  const identity = buildEnrollmentIdentityDisplay(
    enrollment,
    t('dangerZone.installationFallback'),
    identityDisplayCopy(t),
  );
  return identity.accountLabel ?? identity.heroTitle;
};

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

const formatRelativeAge = (
  value: string | undefined,
  t: (key: string, options?: Record<string, string | number>) => string,
  now: number = Date.now(),
) => {
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
    return t('dangerZone.today');
  }
  if (diffDays === 1) {
    return t('dangerZone.oneDayAgo');
  }
  if (diffDays < 7) {
    return t('dangerZone.daysAgo', {count: diffDays});
  }

  const diffWeeks = Math.floor(diffDays / 7);
  if (diffWeeks === 1) {
    return t('dangerZone.oneWeekAgo');
  }
  if (diffWeeks < 5) {
    return t('dangerZone.weeksAgo', {count: diffWeeks});
  }

  const diffMonths = Math.floor(diffDays / 30);
  if (diffMonths === 1) {
    return t('dangerZone.oneMonthAgo');
  }

  return t('dangerZone.monthsAgo', {count: diffMonths});
};

const buildRecencyLabel = (
  enrollment: EnrollmentMetadataRecord,
  t: (key: string, options?: Record<string, string | number>) => string,
  now: number = Date.now(),
) => {
  const lastActivity = formatRelativeAge(enrollment.lastActivityAt, t, now);
  if (lastActivity) {
    return t('dangerZone.lastActive', {value: lastActivity});
  }

  const created = formatRelativeAge(enrollment.createdAt, t, now);
  if (created) {
    return t('dangerZone.enrolled', {value: created});
  }

  return t('dangerZone.enrollmentDateUnavailable');
};

const wasRecentlyActive = (enrollment: EnrollmentMetadataRecord, now: number = Date.now()) => {
  const lastActivityAt = Date.parse(enrollment.lastActivityAt);
  if (Number.isNaN(lastActivityAt)) {
    return false;
  }

  return now - lastActivityAt <= RECENT_ACTIVITY_THRESHOLD_MS;
};

const buildDeleteMessage = (
  enrollment: EnrollmentMetadataRecord,
  t: (key: string, options?: Record<string, string | number>) => string,
) => {
  const lines = [
    t('dangerZone.removeQuestion', {name: getEnrollmentDisplayName(enrollment, t)}),
    '',
    t('dangerZone.integration', {
      value:
        buildHomeCardLabels(
          enrollment,
          t('dangerZone.installationFallback'),
          identityDisplayCopy(t),
        ).title,
    }),
  ];

  const identity = buildEnrollmentIdentityDisplay(
    enrollment,
    t('dangerZone.installationFallback'),
    identityDisplayCopy(t),
  );
  if (identity.roleLabel) {
    lines.push(identity.roleLabel);
  }

  if (identity.tenantLabel) {
    lines.push(t('dangerZone.tenant', {value: identity.tenantLabel}));
  }

  const installationLabel = enrollment.installation?.name || enrollment.installation?.host;
  if (installationLabel) {
    lines.push(t('dangerZone.installation', {value: installationLabel}));
  }

  const lastActivity = formatAbsoluteDateTime(enrollment.lastActivityAt);
  if (lastActivity) {
    lines.push(t('dangerZone.lastActivity', {value: lastActivity}));
  }

  if (enrollment.favorited) {
    lines.push(t('dangerZone.markedFavorite'));
  }

  if (wasRecentlyActive(enrollment)) {
    lines.push(t('dangerZone.usedRecently'));
  }

  lines.push('', t('dangerZone.unlinkWarning'));
  return lines.join('\n');
};

/**
 * Administrative screen for deleting enrollments and clearing all local data.
 * Separated from the main flow to avoid accidental deletion.
 *
 * @since 2025
 */
export const DangerZoneScreen: React.FC = () => {
  const {t} = useTranslation();
  const navigation = useNavigation();
  const {data, isLoading, refetch} = useEnrollments();
  const deleteMutation = useDeleteEnrollment();
  const [clearAllPending, setClearAllPending] = useState(false);
  const brokenIds = useMemo(
    () => new Set((data?.broken ?? []).map(item => item.id)),
    [data],
  );
  // Unusable enrollments stay deletable here — the explicit recovery path of the
  // MOB-015 locked UI contract.
  const sortedEnrollments = useMemo(
    () =>
      sortEnrollments([
        ...(data?.enrollments ?? []),
        ...(data?.broken ?? []).map(item => item.metadata),
      ]),
    [data],
  );

  const handleDelete = useCallback(
    (enrollment: EnrollmentMetadataRecord) => {
      Alert.alert(
        t('dangerZone.deleteTitle'),
        buildDeleteMessage(enrollment, t),
        [
          {text: t('dangerZone.cancel'), style: 'cancel'},
          {
            text: t('dangerZone.delete'),
            style: 'destructive',
            onPress: async () => {
              try {
                await deleteMutation.mutateAsync(enrollment.id);
                navigation.goBack();
              } catch (error) {
                console.error('[DangerZone] Failed to delete', error);
                Alert.alert(t('dangerZone.deleteFailedTitle'), t('dangerZone.deleteFailedMessage'));
              }
            },
          },
        ],
      );
    },
    [deleteMutation, navigation, t],
  );

  const handleClearAllData = useCallback(() => {
    Alert.alert(
      t('dangerZone.clearAllTitle'),
      t('dangerZone.clearAllMessage'),
      [
        {text: t('dangerZone.cancel'), style: 'cancel'},
        {
          text: t('dangerZone.clearAll'),
          style: 'destructive',
          onPress: async () => {
            setClearAllPending(true);
            try {
              await enrollmentStorage.clearAll();
              await refetch();
            } catch (error) {
              Alert.alert(
                t('dangerZone.errorTitle'),
                t('dangerZone.clearAllFailed', {
                  message: error instanceof Error ? error.message : String(error),
                }),
              );
            } finally {
              setClearAllPending(false);
            }
          },
        },
      ],
    );
  }, [refetch, t]);

  if (isLoading) {
    return (
      <View style={styles.loadingContainer} accessibilityLabel={t('dangerZone.loading')}>
        <ActivityIndicator color={colors.primaryLight} accessibilityLabel={t('common.loading')} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.banner}>
        <Text style={styles.bannerTitle}>{t('dangerZone.title')}</Text>
        <Text style={styles.bannerHint}>{t('dangerZone.hint')}</Text>
      </View>
      <FlatList
        data={sortedEnrollments}
        keyExtractor={item => item.id}
        accessibilityLabel={t('dangerZone.listAccessibility')}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyText}>{t('dangerZone.empty')}</Text>
          </View>
        }
        ListFooterComponent={
          <View style={styles.footer}>
            <TouchableOpacity
              style={[styles.clearAllButton, clearAllPending && styles.clearAllButtonDisabled]}
              onPress={handleClearAllData}
              disabled={clearAllPending}
              accessibilityRole="button"
              accessibilityLabel={t('dangerZone.clearAllAccessibility')}
              accessibilityHint={t('dangerZone.clearAllHint')}>
              <Text style={styles.clearAllLabel}>{t('dangerZone.clearAllLabel')}</Text>
            </TouchableOpacity>
            <Text style={styles.footerHint}>{t('dangerZone.footerHint')}</Text>
          </View>
        }
        renderItem={({item}) => {
          const card = buildHomeCardLabels(
            item,
            t('dangerZone.installationFallback'),
            identityDisplayCopy(t),
          );
          const displayName = getEnrollmentDisplayName(item, t);
          const showHostHint = shouldShowInstallationHostHint(item);
          const installationLabel = showHostHint && item.installation?.host
            ? `${item.installation?.name ?? t('dangerZone.installationFallback')} · ${item.installation.host}`
            : item.installation?.name;

          return (
            <View style={styles.row}>
              <View style={styles.rowInfo}>
                <View style={styles.rowTitleLine}>
                  <Text style={styles.rowTitle}>{displayName}</Text>
                  {brokenIds.has(item.id) ? (
                    <Text style={styles.unusableBadge}>{t('dangerZone.unusableBadge')}</Text>
                  ) : null}
                  {item.favorited ? <Text style={styles.favoriteBadge}>{t('dangerZone.favorite')}</Text> : null}
                </View>
                <Text style={styles.rowMeta}>
                  {[card.title, card.roleLabel].filter(Boolean).join(' · ')}
                </Text>
                <Text style={styles.rowMeta}>
                  {[
                    item.isSystemIntegration ? undefined : item.tenantName,
                    installationLabel,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </Text>
                <Text
                  style={[
                    styles.rowRecency,
                    wasRecentlyActive(item) && styles.rowRecencyRecent,
                  ]}>
                  {buildRecencyLabel(item, t)}
                </Text>
              </View>
              <TouchableOpacity
                style={[styles.deleteButton, deleteMutation.isPending && styles.deleteButtonDisabled]}
                onPress={() => handleDelete(item)}
                disabled={deleteMutation.isPending}
                accessibilityRole="button"
                accessibilityLabel={t('dangerZone.deleteAccessibility', {name: displayName})}
                accessibilityHint={t('dangerZone.deleteAccessibilityHint')}>
                <Text style={styles.deleteLabel}>{t('dangerZone.delete')}</Text>
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
  unusableBadge: {
    fontSize: typography.fontSize.xs,
    fontWeight: typography.fontWeight.semibold,
    color: colors.warning,
    backgroundColor: 'rgba(245, 194, 107, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(245, 194, 107, 0.35)',
    borderRadius: borderRadius.xl,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
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
