/*
 * Ezkey - Open Source MFA/Passkey Alternative
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

import React, {useCallback, useLayoutEffect, useMemo} from 'react';
import {
  ActivityIndicator,
  Button,
  SectionList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../../state/enrollmentStore';
import {groupEnrollmentsByTenant, type TenantGroup} from '../../utils/tenantGrouping';

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

type HomeNavigation = NativeStackNavigationProp<RootStackParamList, 'Home'>;

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
  const {data, isLoading} = useEnrollments();
  const setSelected = useEnrollmentStore(store => store.setSelected);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <View style={styles.headerRight}>
          <TouchableOpacity
            onPress={() => navigation.navigate('DangerZone')}
            style={styles.headerButton}
            hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}>
            <Text style={styles.headerButtonLabel}>Manage</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => navigation.navigate('Diagnostics')}
            style={styles.headerButton}
            hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}>
            <Text style={styles.diagnosticsLabel}>Diagnostics</Text>
          </TouchableOpacity>
        </View>
      ),
    });
  }, [navigation]);

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

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Enrollments</Text>
        <Button title="Add" onPress={navigateToWizard} />
      </View>
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator />
        </View>
      ) : (
        <SectionList
          sections={sections}
          keyExtractor={keyExtractor}
          contentContainerStyle={styles.listContent}
          renderItem={renderItem}
          renderSectionHeader={renderSectionHeader}
          ListEmptyComponent={EmptyState}
          stickySectionHeadersEnabled={false}
        />
      )}
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
    <Text style={styles.emptyText}>No enrollments yet. Tap Add to begin.</Text>
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
    paddingHorizontal: 16,
    paddingTop: 16,
    backgroundColor: '#0b0d11',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  headerButton: {
    paddingHorizontal: 4,
    paddingVertical: 2,
  },
  headerButtonLabel: {
    fontSize: 14,
    fontWeight: '500',
    color: '#5a9cf7',
  },
  diagnosticsLabel: {
    fontSize: 12,
    fontWeight: '500',
    color: '#1b2130',
    opacity: 0.25,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listContent: {
    gap: 12,
    paddingBottom: 32,
  },
  tenantHeader: {
    marginTop: 14,
    paddingHorizontal: 0,
  },
  tenantName: {
    fontSize: 15,
    fontWeight: '700',
    color: '#f4f7ff',
  },
  tenantDescription: {
    marginTop: 2,
    fontSize: 13,
    color: '#9aa3b6',
    lineHeight: 18,
  },
  tenantDivider: {
    height: 1,
    marginTop: 10,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  card: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  status: {
    fontSize: 12,
    fontWeight: '700',
    color: '#61d095',
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#c2c8d5',
    marginBottom: 4,
  },
  cardMeta: {
    fontSize: 12,
    color: '#9aa3b6',
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
  },
  emptyText: {
    color: '#9aa3b6',
    fontSize: 16,
  },
});
