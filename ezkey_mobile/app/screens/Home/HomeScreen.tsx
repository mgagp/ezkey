/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: HomeScreen
 * Description: Landing screen that lists device enrollments and routes users into detail and authentication flows.
 * Security Context: Follows UX guidelines from docs/features/AUTH_SECURITY.md by requiring explicit navigation before
 *                   polling for pending auth attempts, preventing accidental proof token reuse.
 * @since 2025
 */

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation} from '@react-navigation/native';
import {StackNavigationProp} from '@react-navigation/stack';
import {useEnrollments, useRefreshInstallationMetadata} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {
  groupEnrollmentsByTenant,
  type InstallationGroup,
} from '../../utils/tenantGrouping';
import {borderRadius, colors, spacing, typography} from '../../config/theme';

/**
 * Orders enrollments prioritizing favorites while preserving newest-first semantics.
 *
 * @param items Enrollment array to sort.
 * @return Sorted enrollment array.
 * @since 2025
 */
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

type HomeNavigation = StackNavigationProp<RootStackParamList, 'Home'>;

/**
 * Lists stored enrollments and routes users to detail or wizard screens.
 *
 * @since 2025
 */
export const HomeScreen: React.FC = () => {
  const navigation = useNavigation<HomeNavigation>();
  const insets = useSafeAreaInsets();
  const {data, isLoading} = useEnrollments();
  const refreshInstallationMetadata = useRefreshInstallationMetadata();
  const {isPending: isRefreshingInstallationMetadata, mutate: refreshInstallations} =
    refreshInstallationMetadata;
  const setSelected = useEnrollmentStore(store => store.setSelected);
  const [expandedInstallations, setExpandedInstallations] = useState<string[]>([]);

  const installationGroups = useMemo(() => {
    if (!data || data.length === 0) return [];
    const sorted = sortEnrollments(data);
    return groupEnrollmentsByTenant(sorted);
  }, [data]);

  useEffect(() => {
    if (installationGroups.length === 0) {
      setExpandedInstallations([]);
      return;
    }

    setExpandedInstallations(previous => {
      if (installationGroups.length === 1) {
        return [installationGroups[0].installationId];
      }

      const kept = previous.filter(id =>
        installationGroups.some(group => group.installationId === id),
      );

      return kept.length > 0 ? kept : [installationGroups[0].installationId];
    });
  }, [installationGroups]);

  useEffect(() => {
    if (!data || data.length === 0 || isRefreshingInstallationMetadata) {
      return;
    }

    refreshInstallations(data);
  }, [data, isRefreshingInstallationMetadata, refreshInstallations]);

  const navigateToWizard = () => navigation.navigate('EnrollmentWizard');

  const handleSelect = useCallback(
    (enrollment: StoredEnrollment) => {
      setSelected(enrollment.id);
      navigation.navigate('EnrollmentDetail', {enrollmentId: enrollment.id});
    },
    [navigation, setSelected],
  );

  const toggleInstallation = useCallback((installationId: string) => {
    setExpandedInstallations(previous =>
      previous.includes(installationId)
        ? previous.filter(id => id !== installationId)
        : [...previous, installationId],
    );
  }, []);

  const fabBottom = insets.bottom + spacing.lg;

  return (
    <View style={styles.container}>
      {isLoading ? (
        <View style={styles.loadingContainer} accessibilityLabel="Loading enrollments">
          <ActivityIndicator color={colors.primaryLight} accessibilityLabel="Loading" />
        </View>
      ) : (
        <ScrollView
          contentContainerStyle={[styles.listContent, {paddingBottom: fabBottom + 56 + spacing.md}]}
          accessibilityLabel="Enrollments grouped by Ezkey installation">
          {installationGroups.length === 0 ? (
            <EmptyState />
          ) : (
            installationGroups.map(group => (
              <InstallationSection
                key={group.installationId}
                group={group}
                expanded={expandedInstallations.includes(group.installationId)}
                onToggle={toggleInstallation}
                onSelectEnrollment={handleSelect}
              />
            ))
          )}
        </ScrollView>
      )}
      <TouchableOpacity
        style={[styles.fab, {bottom: fabBottom}]}
        onPress={navigateToWizard}
        activeOpacity={0.85}
        accessibilityRole="button"
        accessibilityLabel="Add enrollment"
        accessibilityHint="Starts enrollment with QR scan">
        <Text style={styles.fabLabel}>+</Text>
      </TouchableOpacity>
    </View>
  );
};

/**
 * Fallback component rendered when no enrollments exist locally. Acts as the first-launch onboarding
 * surface, explaining the app's purpose and the QR-scan entry point.
 *
 * @since 2025
 */
const EmptyState: React.FC = () => (
  <View
    style={styles.emptyState}
    accessibilityRole="text"
    accessibilityLabel="Welcome to Ezkey. No enrollments yet. Tap the plus button to scan a QR code and add your first device.">
    <Text style={styles.emptyTitle}>Welcome to Ezkey</Text>
    <Text style={styles.emptySubtitle}>
      Approve sign-ins from your trusted device.
    </Text>
    <View style={styles.emptyHintCard}>
      <Text style={styles.emptyHintBadge}>QR</Text>
      <Text style={styles.emptyHintText}>
        Tap the + button below and scan a QR code from your Ezkey-enabled service to enroll your first device.
      </Text>
    </View>
  </View>
);

type TenantSectionHeaderProps = {
  tenantName: string;
  tenantDescription?: string;
};

/**
 * Renders a tenant section header with name and optional description.
 *
 * @param tenantName Display name of the tenant.
 * @param tenantDescription Optional description of the tenant.
 * @since 2025
 */
const TenantSectionHeader: React.FC<TenantSectionHeaderProps> = ({
  tenantName,
  tenantDescription,
}) => (
  <View style={styles.tenantHeader} accessibilityLabel={`Section ${tenantName}`}>
    <Text style={styles.tenantName}>{tenantName}</Text>
    {tenantDescription ? (
      <Text style={styles.tenantDescription}>{tenantDescription}</Text>
    ) : null}
    <View style={styles.tenantDivider} />
  </View>
);

type InstallationSectionProps = {
  group: InstallationGroup;
  expanded: boolean;
  onToggle: (installationId: string) => void;
  onSelectEnrollment: (enrollment: StoredEnrollment) => void;
};

const InstallationSection: React.FC<InstallationSectionProps> = ({
  group,
  expanded,
  onToggle,
  onSelectEnrollment,
}) => (
  <View style={styles.installationShell}>
    <TouchableOpacity
      style={styles.installationHeader}
      onPress={() => onToggle(group.installationId)}
      accessibilityRole="button"
      accessibilityLabel={`${group.installationName} installation`}
      accessibilityHint={expanded ? 'Collapses this installation section' : 'Expands this installation section'}>
      <View style={styles.installationHeaderContent}>
        <Text style={styles.installationName}>{group.installationName}</Text>
        {group.installationDescription ? (
          <Text style={styles.installationDescription}>{group.installationDescription}</Text>
        ) : null}
        {group.showHostHint && group.installationHost ? (
          <Text style={styles.installationHost}>{group.installationHost}</Text>
        ) : null}
      </View>
      <Text style={styles.installationToggle}>{expanded ? '−' : '+'}</Text>
    </TouchableOpacity>
    {expanded ? (
      <View style={styles.installationBody}>
        {group.tenantGroups.map(tenantGroup => (
          <View key={`${group.installationId}:${tenantGroup.tenantId ?? tenantGroup.tenantName}`}>
            <TenantSectionHeader
              tenantName={tenantGroup.tenantName}
              tenantDescription={tenantGroup.tenantDescription}
            />
            {tenantGroup.enrollments.map(enrollment => (
              <EnrollmentListItem
                key={enrollment.id}
                enrollment={enrollment}
                onPress={onSelectEnrollment}
              />
            ))}
          </View>
        ))}
        {group.ungroupedEnrollments.map(enrollment => (
          <EnrollmentListItem
            key={enrollment.id}
            enrollment={enrollment}
            onPress={onSelectEnrollment}
          />
        ))}
      </View>
    ) : null}
  </View>
);

type EnrollmentListItemProps = {
  enrollment: StoredEnrollment;
  onPress: (enrollment: StoredEnrollment) => void;
};

/**
 * Renders enrollment metadata within the home list.
 *
 * @param enrollment Enrollment to display.
 * @param onPress Callback invoked when the item is selected.
 * @since 2025
 */
const EnrollmentListItem: React.FC<EnrollmentListItemProps> = ({enrollment, onPress}) => (
  <TouchableOpacity
    style={styles.card}
    onPress={() => onPress(enrollment)}
    accessibilityRole="button"
    accessibilityLabel={`${enrollment.integrationName}, ${enrollment.status}`}
    accessibilityHint="Opens enrollment details">
    <View style={styles.cardHeader}>
      <Text style={styles.cardTitle}>{enrollment.integrationName}</Text>
      <Text style={styles.status}>{enrollment.status.toUpperCase()}</Text>
    </View>
    {enrollment.enrollmentName ? (
      <Text style={styles.cardSubtitle}>{enrollment.enrollmentName}</Text>
    ) : null}
    {enrollment.integrationDescription ? (
      <Text numberOfLines={2} style={styles.cardDescription}>
        {enrollment.integrationDescription}
      </Text>
    ) : null}
  </TouchableOpacity>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    backgroundColor: colors.background,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listContent: {
    gap: spacing.xl,
    paddingBottom: spacing.xxl,
  },
  installationShell: {
    borderWidth: 1,
    borderColor: colors.borderFocusStrong,
    borderRadius: borderRadius.lg,
    overflow: 'hidden',
    backgroundColor: colors.installationShellBg,
  },
  installationHeader: {
    backgroundColor: colors.surface,
    padding: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  installationHeaderContent: {
    flex: 1,
    paddingRight: spacing.md,
  },
  installationName: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.bold,
    color: colors.textPrimary,
  },
  installationDescription: {
    marginTop: 4,
    fontSize: typography.fontSize.sm,
    color: colors.textSecondary,
    lineHeight: 18,
  },
  installationHost: {
    marginTop: 6,
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
  },
  installationToggle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.bold,
    color: colors.primaryLight,
  },
  installationBody: {
    gap: spacing.md,
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.md,
  },
  tenantHeader: {
    marginTop: spacing.sm,
    paddingHorizontal: spacing.xs,
  },
  tenantName: {
    fontSize: typography.fontSize.md,
    fontWeight: typography.fontWeight.bold,
    color: colors.textPrimary,
  },
  tenantDescription: {
    marginTop: 2,
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    lineHeight: 18,
  },
  tenantDivider: {
    height: 1,
    marginTop: spacing.sm + 2,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  card: {
    backgroundColor: colors.surfaceElevated,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  cardTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  status: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.bold,
    color: colors.success,
  },
  cardSubtitle: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: 4,
  },
  cardDescription: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    lineHeight: 18,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
    paddingHorizontal: spacing.lg,
  },
  emptyTitle: {
    color: colors.textPrimary,
    fontSize: typography.fontSize.title,
    fontWeight: typography.fontWeight.semibold,
    textAlign: 'center',
  },
  emptySubtitle: {
    color: colors.textSecondary,
    fontSize: typography.fontSize.lg,
    textAlign: 'center',
    marginTop: spacing.sm,
    lineHeight: 22,
  },
  emptyHintCard: {
    marginTop: spacing.xl,
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    alignSelf: 'stretch',
  },
  emptyHintBadge: {
    width: 44,
    height: 44,
    borderRadius: borderRadius.md,
    backgroundColor: colors.surfaceElevated,
    color: colors.primaryLight,
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.bold,
    textAlign: 'center',
    textAlignVertical: 'center',
    lineHeight: 44,
    overflow: 'hidden',
  },
  emptyHintText: {
    flex: 1,
    color: colors.textSecondary,
    fontSize: typography.fontSize.base,
    lineHeight: 20,
  },
  fab: {
    position: 'absolute',
    right: spacing.lg,
    width: 56,
    height: 56,
    borderRadius: borderRadius.xl + 12,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  fabLabel: {
    fontSize: 28,
    fontWeight: '300',
    color: colors.textOnPrimary,
    lineHeight: 32,
  },
});
