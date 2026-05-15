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
import {useTranslation} from 'react-i18next';
import {useEnrollments, useRefreshInstallationMetadata} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {
  groupEnrollmentsByInstallation,
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
  const {t} = useTranslation();
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
    return groupEnrollmentsByInstallation(sorted);
  }, [data]);

  useEffect(() => {
    if (installationGroups.length === 0) {
      setExpandedInstallations([]);
      return;
    }

    setExpandedInstallations(previous => {
      if (installationGroups.length === 1) {
        return [installationGroups[0].installation.id];
      }

      const kept = previous.filter(id =>
        installationGroups.some(group => group.installation.id === id),
      );

      if (kept.length > 0) {
        return kept;
      }
      /*
       * Default: expand every installation section when the list is modest so Home rows (and Maestro
       * testIDs on enrollments) exist in the accessibility tree. If only the first section were open,
       * enrollments under other installations would be absent from the hierarchy until the user taps
       * "+", which breaks unattended flows and confuses automation.
       */
      return installationGroups.length <= 6
        ? installationGroups.map(group => group.installation.id)
        : [installationGroups[0].installation.id];
    });
  }, [installationGroups]);

  useEffect(() => {
    if (!data || data.length === 0 || isRefreshingInstallationMetadata) {
      return;
    }

    refreshInstallations(data);
  }, [data, isRefreshingInstallationMetadata, refreshInstallations]);

  const navigateToWizard = () => navigation.navigate('EnrollmentWizard');
  const navigateToReleaseNotes = () => navigation.navigate('ReleaseNotes');

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
    <View style={styles.container} testID="ezkey.e2e.home.root" collapsable={false}>
      {isLoading ? (
        <View style={styles.loadingContainer} accessibilityLabel={t('home.loadingEnrollments')}>
          <ActivityIndicator color={colors.primaryLight} accessibilityLabel={t('home.loading')} />
        </View>
      ) : (
        <ScrollView
          contentContainerStyle={[styles.listContent, {paddingBottom: fabBottom + 56 + spacing.md}]}
          accessibilityLabel={t('home.groupedByInstallation')}>
          <TouchableOpacity
            style={styles.releaseBanner}
            onPress={navigateToReleaseNotes}
            activeOpacity={0.9}
            accessibilityRole="button"
            accessibilityLabel={t('home.releaseBannerTitle')}
            accessibilityHint={t('home.releaseBannerHint')}>
            <Text style={styles.releaseBannerEyebrow}>{t('home.releaseBannerEyebrow')}</Text>
            <Text style={styles.releaseBannerTitle}>{t('home.releaseBannerTitle')}</Text>
            <Text style={styles.releaseBannerBody}>{t('home.releaseBannerBody')}</Text>
            <Text style={styles.releaseBannerLink}>{t('home.releaseBannerAction')}</Text>
          </TouchableOpacity>
          {installationGroups.length === 0 ? (
            <EmptyState />
          ) : (
            installationGroups.map(group => (
              <InstallationSection
                key={group.installation.id}
                group={group}
                expanded={expandedInstallations.includes(group.installation.id)}
                onToggle={toggleInstallation}
                onSelectEnrollment={handleSelect}
              />
            ))
          )}
        </ScrollView>
      )}
      <TouchableOpacity
        testID="ezkey.e2e.home.fabAddEnrollment"
        style={[styles.fab, {bottom: fabBottom}]}
        onPress={navigateToWizard}
        activeOpacity={0.85}
        accessibilityRole="button"
        accessibilityLabel={t('home.addEnrollment')}
        accessibilityHint={t('home.addEnrollmentHint')}>
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
const EmptyState: React.FC = () => {
  const {t} = useTranslation();

  return (
    <View
      style={styles.emptyState}
      accessibilityRole="text"
      accessibilityLabel={t('home.emptyAccessibilityLabel')}>
      <Text style={styles.emptyTitle}>{t('home.emptyTitle')}</Text>
      <Text style={styles.emptySubtitle}>{t('home.emptySubtitle')}</Text>
      <View style={styles.emptyHintCard}>
        <Text style={styles.emptyHintBadge}>QR</Text>
        <Text style={styles.emptyHintText}>{t('home.emptyHint')}</Text>
      </View>
    </View>
  );
};

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
}) => {
  const {t} = useTranslation();

  return (
    <View
      style={styles.tenantHeader}
      accessibilityLabel={t('home.tenantSection', {tenantName})}>
      <Text style={styles.tenantName}>{tenantName}</Text>
      {tenantDescription ? (
        <Text style={styles.tenantDescription}>{tenantDescription}</Text>
      ) : null}
      <View style={styles.tenantDivider} />
    </View>
  );
};

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
}) => {
  const {t} = useTranslation();

  return (
    <View style={styles.installationShell}>
      <TouchableOpacity
        testID={`ezkey.e2e.home.installation.${group.installation.id}`}
        style={styles.installationHeader}
        onPress={() => onToggle(group.installation.id)}
        accessibilityRole="button"
        accessibilityLabel={t('home.installationLabel', {name: group.installation.name})}
        accessibilityHint={
          expanded ? t('home.collapseInstallation') : t('home.expandInstallation')
        }>
        <View style={styles.installationHeaderContent}>
          <Text style={styles.installationName}>{group.installation.name}</Text>
          {group.installation.description ? (
            <Text style={styles.installationDescription}>{group.installation.description}</Text>
          ) : null}
          {group.showHostHint && group.installation.host ? (
            <Text style={styles.installationHost}>{group.installation.host}</Text>
          ) : null}
        </View>
        <Text style={styles.installationToggle}>{expanded ? '−' : '+'}</Text>
      </TouchableOpacity>
      {expanded ? (
        <View style={styles.installationBody}>
          {group.tenantGroups.map(tenantGroup => (
            <View key={`${group.installation.id}:${tenantGroup.tenantId ?? tenantGroup.tenantName}`}>
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
};

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
const EnrollmentListItem: React.FC<EnrollmentListItemProps> = ({enrollment, onPress}) => {
  const {t} = useTranslation();

  return (
    <TouchableOpacity
      testID={`ezkey.e2e.home.enrollment.${enrollment.id}`}
      style={styles.card}
      onPress={() => onPress(enrollment)}
      accessibilityRole="button"
      accessibilityLabel={enrollment.integrationName}
      accessibilityHint={t('home.openEnrollmentDetails')}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>{enrollment.integrationName}</Text>
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
};

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
  releaseBanner: {
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.borderFocus,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  releaseBannerEyebrow: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.semibold,
    color: colors.warning,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  releaseBannerTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  releaseBannerBody: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  releaseBannerLink: {
    marginTop: spacing.xs,
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.primaryLight,
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
