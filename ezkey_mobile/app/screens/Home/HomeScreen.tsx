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

import React, {useCallback, useMemo} from 'react';
import {
  ActivityIndicator,
  SectionList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useNavigation} from '@react-navigation/native';
import {StackNavigationProp} from '@react-navigation/stack';
import {useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {groupEnrollmentsByTenant, type TenantGroup} from '../../utils/tenantGrouping';
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
 * Converts tenant groups to SectionList sections format.
 *
 * @param groups Tenant groups from groupEnrollmentsByTenant.
 * @return Sections for SectionList.
 * @since 2025
 */
const toSections = (groups: TenantGroup[]): Array<{group: TenantGroup; data: StoredEnrollment[]}> =>
  groups.map(group => ({group, data: group.enrollments}));

/**
 * Lists stored enrollments and routes users to detail or wizard screens.
 *
 * @since 2025
 */
export const HomeScreen: React.FC = () => {
  const navigation = useNavigation<HomeNavigation>();
  const insets = useSafeAreaInsets();
  const {data, isLoading} = useEnrollments();
  const setSelected = useEnrollmentStore(store => store.setSelected);

  const sections = useMemo(() => {
    if (!data || data.length === 0) return [];
    const sorted = sortEnrollments(data);
    const groups = groupEnrollmentsByTenant(sorted);
    return toSections(groups);
  }, [data]);

  const navigateToWizard = () => navigation.navigate('EnrollmentWizard');

  const handleSelect = useCallback(
    (enrollment: StoredEnrollment) => {
      setSelected(enrollment.id);
      navigation.navigate('EnrollmentDetail', {enrollmentId: enrollment.id});
    },
    [navigation, setSelected],
  );

  const renderItem = useCallback(
    ({item}: {item: StoredEnrollment}) => (
      <EnrollmentListItem enrollment={item} onPress={handleSelect} />
    ),
    [handleSelect],
  );

  const renderSectionHeader = useCallback(
    ({section}: {section: {group: TenantGroup; data: StoredEnrollment[]}}) => (
      <TenantSectionHeader tenantName={section.group.tenantName} tenantDescription={section.group.tenantDescription} />
    ),
    [],
  );

  const keyExtractor = useCallback((item: StoredEnrollment) => item.id, []);

  const fabBottom = insets.bottom + spacing.lg;

  return (
    <View style={styles.container}>
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator color={colors.primaryLight} />
        </View>
      ) : (
        <SectionList
          sections={sections}
          keyExtractor={keyExtractor}
          contentContainerStyle={[styles.listContent, {paddingBottom: fabBottom + 56 + spacing.md}]}
          renderItem={renderItem}
          renderSectionHeader={renderSectionHeader}
          ListEmptyComponent={EmptyState}
          stickySectionHeadersEnabled={false}
        />
      )}
      <TouchableOpacity
        style={[styles.fab, {bottom: fabBottom}]}
        onPress={navigateToWizard}
        activeOpacity={0.85}
        accessibilityRole="button"
        accessibilityLabel="Add enrollment">
        <Text style={styles.fabLabel}>+</Text>
      </TouchableOpacity>
    </View>
  );
};

/**
 * Fallback component rendered when no enrollments exist locally.
 *
 * @since 2025
 */
const EmptyState: React.FC = () => (
  <View style={styles.emptyState}>
    <Text style={styles.emptyText}>No enrollments yet. Tap + to add one.</Text>
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
  <TouchableOpacity style={styles.card} onPress={() => onPress(enrollment)}>
    <View style={styles.cardHeader}>
      <Text style={styles.cardTitle}>{enrollment.integrationName}</Text>
      <Text style={styles.status}>{enrollment.status.toUpperCase()}</Text>
    </View>
    {enrollment.enrollmentName ? (
      <Text style={styles.cardSubtitle}>{enrollment.enrollmentName}</Text>
    ) : null}
    <Text style={styles.cardMeta}>
      Created {new Date(enrollment.createdAt).toLocaleDateString()}
    </Text>
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
    gap: spacing.md,
    paddingBottom: spacing.xxl,
  },
  tenantHeader: {
    marginTop: spacing.md,
    paddingHorizontal: 0,
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
    backgroundColor: colors.surface,
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
  cardMeta: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
  },
  emptyText: {
    color: colors.textMuted,
    fontSize: typography.fontSize.lg,
    textAlign: 'center',
    paddingHorizontal: spacing.xl,
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
