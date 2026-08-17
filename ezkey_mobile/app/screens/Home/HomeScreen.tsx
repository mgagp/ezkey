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
  Alert,
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
import {
  useDeleteEnrollment,
  useEnrollments,
  useRefreshInstallationMetadata,
} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {EnrollmentMetadataRecord} from '../../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {
  groupEnrollmentsByInstallation,
  type InstallationGroup,
} from '../../utils/tenantGrouping';
import {borderRadius, colors, spacing, typography} from '../../config/theme';
import {
  checkFlexiblePlayUpdate,
  getDismissedPlayUpdateVersionCode,
  setDismissedPlayUpdateVersionCode,
  startFlexiblePlayUpdate,
} from '../../services/play/playUpdate';

/**
 * Orders enrollments prioritizing favorites while preserving newest-first semantics.
 *
 * @param items Enrollment array to sort.
 * @return Sorted enrollment array.
 * @since 2025
 */
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
  const deleteEnrollment = useDeleteEnrollment();
  const setSelected = useEnrollmentStore(store => store.setSelected);
  const [expandedInstallations, setExpandedInstallations] = useState<string[]>([]);

  const healthyEnrollments = data?.enrollments;
  const brokenEnrollments = data?.broken;
  const collectionError = data?.collectionError ?? false;

  const brokenIds = useMemo(
    () => new Set((brokenEnrollments ?? []).map(item => item.id)),
    [brokenEnrollments],
  );

  const installationGroups = useMemo(() => {
    const rows: EnrollmentMetadataRecord[] = [
      ...(healthyEnrollments ?? []),
      ...(brokenEnrollments ?? []).map(item => item.metadata),
    ];
    if (rows.length === 0) return [];
    return groupEnrollmentsByInstallation(sortEnrollments(rows));
  }, [healthyEnrollments, brokenEnrollments]);

  const showUnusableLocalNotice =
    collectionError || ((healthyEnrollments?.length ?? 0) === 0 && brokenIds.size > 0);

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
    if (!healthyEnrollments || healthyEnrollments.length === 0 || isRefreshingInstallationMetadata) {
      return;
    }

    refreshInstallations(healthyEnrollments);
  }, [healthyEnrollments, isRefreshingInstallationMetadata, refreshInstallations]);

  useEffect(() => {
    let cancelled = false;

    const promptPlayUpdate = async () => {
      const result = await checkFlexiblePlayUpdate();
      if (cancelled || !result.available) {
        return;
      }
      const dismissed = await getDismissedPlayUpdateVersionCode();
      if (cancelled) {
        return;
      }
      if (
        result.availableVersionCode != null &&
        dismissed === result.availableVersionCode
      ) {
        return;
      }
      Alert.alert(t('home.updateAvailableTitle'), t('home.updateAvailableBody'), [
        {
          text: t('home.updateAvailableLater'),
          style: 'cancel',
          onPress: () => {
            if (result.availableVersionCode != null) {
              void setDismissedPlayUpdateVersionCode(result.availableVersionCode);
            }
          },
        },
        {
          text: t('home.updateAvailableAction'),
          onPress: () => {
            void startFlexiblePlayUpdate();
          },
        },
      ]);
    };

    void promptPlayUpdate();
    return () => {
      cancelled = true;
    };
  }, [t]);

  const navigateToWizard = () => navigation.navigate('EnrollmentWizard');
  const navigateToReleaseNotes = () => navigation.navigate('ReleaseNotes');

  const handleSelect = useCallback(
    (enrollment: EnrollmentMetadataRecord) => {
      setSelected(enrollment.id);
      navigation.navigate('EnrollmentDetail', {enrollmentId: enrollment.id});
    },
    [navigation, setSelected],
  );

  const handleRemoveBroken = useCallback(
    (enrollment: EnrollmentMetadataRecord) => {
      Alert.alert(
        t('home.brokenRemoveConfirmTitle'),
        t('home.brokenRemoveConfirmMessage', {name: enrollment.integrationName}),
        [
          {text: t('home.brokenRemoveCancel'), style: 'cancel'},
          {
            text: t('home.brokenRowRemove'),
            style: 'destructive',
            onPress: () => deleteEnrollment.mutate(enrollment.id),
          },
        ],
      );
    },
    [deleteEnrollment, t],
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
          {showUnusableLocalNotice ? <UnusableLocalDataNotice /> : null}
          {installationGroups.length === 0 ? (
            collectionError ? null : <EmptyState />
          ) : (
            installationGroups.map(group => (
              <InstallationSection
                key={group.installation.id}
                group={group}
                expanded={expandedInstallations.includes(group.installation.id)}
                onToggle={toggleInstallation}
                onSelectEnrollment={handleSelect}
                brokenIds={brokenIds}
                onRemoveBroken={handleRemoveBroken}
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

/**
 * Honest local-state notice rendered when saved enrollment data is unusable (all rows broken or
 * the collection is unreadable). Deliberately distinct from the first-use welcome empty state so
 * storage/crypto failure never presents as "no enrollments" (MOB-015 locked UI contract).
 *
 * @since 2026
 */
const UnusableLocalDataNotice: React.FC = () => {
  const {t} = useTranslation();

  return (
    <View
      style={styles.unusableNotice}
      testID="ezkey.e2e.home.unusableLocalData"
      accessibilityRole="text"
      accessibilityLabel={t('home.unusableLocalTitle')}>
      <Text style={styles.unusableNoticeTitle}>{t('home.unusableLocalTitle')}</Text>
      <Text style={styles.unusableNoticeBody}>{t('home.unusableLocalSubtitle')}</Text>
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
  onSelectEnrollment: (enrollment: EnrollmentMetadataRecord) => void;
  brokenIds: Set<string>;
  onRemoveBroken: (enrollment: EnrollmentMetadataRecord) => void;
};

const InstallationSection: React.FC<InstallationSectionProps> = ({
  group,
  expanded,
  onToggle,
  onSelectEnrollment,
  brokenIds,
  onRemoveBroken,
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
                  broken={brokenIds.has(enrollment.id)}
                  onRemove={onRemoveBroken}
                />
              ))}
            </View>
          ))}
          {group.ungroupedEnrollments.map(enrollment => (
            <EnrollmentListItem
              key={enrollment.id}
              enrollment={enrollment}
              onPress={onSelectEnrollment}
              broken={brokenIds.has(enrollment.id)}
              onRemove={onRemoveBroken}
            />
          ))}
        </View>
      ) : null}
    </View>
  );
};

type EnrollmentListItemProps = {
  enrollment: EnrollmentMetadataRecord;
  onPress: (enrollment: EnrollmentMetadataRecord) => void;
  broken: boolean;
  onRemove: (enrollment: EnrollmentMetadataRecord) => void;
};

/**
 * Renders enrollment metadata within the home list.
 *
 * Enrollments whose local secrets are unusable stay visible with an honest "unusable" treatment
 * and a remove action instead of silently disappearing (MOB-015 locked UI contract).
 *
 * @param enrollment Enrollment to display.
 * @param onPress Callback invoked when the item is selected.
 * @param broken True when the enrollment's local secrets are unusable.
 * @param onRemove Callback invoked when the user removes a broken enrollment.
 * @since 2025
 */
const EnrollmentListItem: React.FC<EnrollmentListItemProps> = ({
  enrollment,
  onPress,
  broken,
  onRemove,
}) => {
  const {t} = useTranslation();

  return (
    <TouchableOpacity
      testID={`ezkey.e2e.home.enrollment.${enrollment.id}`}
      style={[styles.card, broken && styles.cardBroken]}
      onPress={() => onPress(enrollment)}
      accessibilityRole="button"
      accessibilityLabel={
        broken
          ? t('home.brokenRowAccessibility', {name: enrollment.integrationName})
          : enrollment.integrationName
      }
      accessibilityHint={t('home.openEnrollmentDetails')}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>{enrollment.integrationName}</Text>
        {broken ? <Text style={styles.brokenBadge}>{t('home.brokenBadge')}</Text> : null}
      </View>
      {enrollment.enrollmentName ? (
        <Text style={styles.cardSubtitle}>{enrollment.enrollmentName}</Text>
      ) : null}
      {broken ? (
        <>
          <Text style={styles.brokenSubtitle}>{t('home.brokenRowSubtitle')}</Text>
          <TouchableOpacity
            testID={`ezkey.e2e.home.enrollment.${enrollment.id}.remove`}
            style={styles.brokenRemoveButton}
            onPress={() => onRemove(enrollment)}
            accessibilityRole="button"
            accessibilityLabel={t('home.brokenRowRemoveAccessibility', {
              name: enrollment.integrationName,
            })}>
            <Text style={styles.brokenRemoveLabel}>{t('home.brokenRowRemove')}</Text>
          </TouchableOpacity>
        </>
      ) : enrollment.integrationDescription ? (
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
  unusableNotice: {
    backgroundColor: 'rgba(245, 194, 107, 0.08)',
    borderWidth: 1,
    borderColor: 'rgba(245, 194, 107, 0.35)',
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  unusableNoticeTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.warning,
  },
  unusableNoticeBody: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  cardBroken: {
    borderWidth: 1,
    borderColor: 'rgba(245, 194, 107, 0.45)',
  },
  brokenBadge: {
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
  brokenSubtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textSecondary,
    lineHeight: 18,
    marginTop: 2,
  },
  brokenRemoveButton: {
    marginTop: spacing.md,
    alignSelf: 'flex-start',
    paddingVertical: 8,
    paddingHorizontal: spacing.lg,
    borderRadius: borderRadius.sm,
    backgroundColor: colors.error,
  },
  brokenRemoveLabel: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textOnPrimary,
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
