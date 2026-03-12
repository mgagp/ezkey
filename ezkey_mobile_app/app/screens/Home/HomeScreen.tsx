import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Pressable,
  ActivityIndicator,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {RootStackParamList} from '../../navigation/types';
import {EzkeyLogo} from '../../components/EzkeyLogo';
import {colors, spacing, typography, borderRadius} from '../../config/theme';
import {useEnrollments} from '../../hooks/useEnrollments';
import {groupEnrollmentsByTenant} from '../../utils/tenantGrouping';

type Props = StackScreenProps<RootStackParamList, 'Home'>;

export const HomeScreen: React.FC<Props> = ({navigation}) => {
  const insets = useSafeAreaInsets();
  const {data: enrollments = [], isLoading} = useEnrollments();
  const groups = groupEnrollmentsByTenant(enrollments);
  const hasEnrollments = enrollments.length > 0;

  const openAddEnrollment = () => navigation.navigate('EnrollmentFlow');
  const openEnrollmentDetail = (enrollmentId: string) =>
    navigation.navigate('EnrollmentDetail', {enrollmentId});

  if (isLoading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={[styles.container, {paddingBottom: insets.bottom + 80}]}>
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}>
        {!hasEnrollments ? (
          <View style={styles.emptyState}>
            <EzkeyLogo width={100} height={100} />
            <Text style={styles.emptyTitle}>No enrollments yet</Text>
            <Text style={styles.emptySubtitle}>
              Add an enrollment by scanning a QR code from your admin console.
            </Text>
          </View>
        ) : (
          <View style={styles.list}>
            {groups.map((group) => (
              <View key={group.tenantName} style={styles.group}>
                <Text style={styles.groupTitle}>{group.tenantName}</Text>
                {group.enrollments.map((e) => (
                  <Pressable
                    key={e.id}
                    style={({pressed}) => [
                      styles.card,
                      pressed && styles.cardPressed,
                    ]}
                    onPress={() => openEnrollmentDetail(e.id)}>
                    <Text style={styles.cardTitle}>{e.integrationName}</Text>
                    <Text style={styles.cardMeta}>
                      {new Date(e.lastActivityAt).toLocaleDateString(undefined, {
                        dateStyle: 'medium',
                      })}
                    </Text>
                  </Pressable>
                ))}
              </View>
            ))}
          </View>
        )}
      </ScrollView>

      <TouchableOpacity
        style={[styles.fab, {bottom: insets.bottom + 24}]}
        onPress={openAddEnrollment}
        activeOpacity={0.85}>
        <Text style={styles.fabLabel}>+</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.lg,
    paddingBottom: spacing.xxl,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: spacing.xxl * 2,
  },
  emptyTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    marginTop: spacing.xl,
  },
  emptySubtitle: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.sm,
    paddingHorizontal: spacing.lg,
  },
  list: {
    gap: spacing.xl,
  },
  group: {
    gap: spacing.md,
  },
  groupTitle: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.semibold,
    color: colors.primaryMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.xs,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cardPressed: {
    opacity: 0.9,
  },
  cardTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  cardMeta: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
  },
  fab: {
    position: 'absolute',
    right: spacing.xl,
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
