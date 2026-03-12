import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {RootStackParamList} from '../../navigation/types';
import {colors, spacing, typography, borderRadius} from '../../config/theme';
import {useEnrollmentById} from '../../hooks/useEnrollments';

type Props = StackScreenProps<RootStackParamList, 'EnrollmentDetail'>;

export const EnrollmentDetailScreen: React.FC<Props> = ({route, navigation}) => {
  const insets = useSafeAreaInsets();
  const {enrollmentId} = route.params;
  const {data: enrollment, isLoading} = useEnrollmentById(enrollmentId);

  const onCheckPending = () => {
    navigation.navigate('PendingAuth', {enrollmentId});
  };

  if (isLoading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }
  if (!enrollment) {
    return (
      <View style={styles.container}>
        <Text style={styles.errorText}>Enrollment not found</Text>
      </View>
    );
  }

  const created = new Date(enrollment.createdAt).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const lastActivity = new Date(enrollment.lastActivityAt).toLocaleString(
    undefined,
    {dateStyle: 'medium', timeStyle: 'short'},
  );

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      keyboardShouldPersistTaps="handled">
      <View style={styles.card}>
        <Text style={styles.cardTitle}>{enrollment.integrationName}</Text>
        <Text style={styles.row}>
          <Text style={styles.label}>Tenant: </Text>
          {enrollment.tenantName}
        </Text>
        <Text style={styles.row}>
          <Text style={styles.label}>Created: </Text>
          {created}
        </Text>
        <Text style={styles.row}>
          <Text style={styles.label}>Last activity: </Text>
          {lastActivity}
        </Text>
        <Text style={styles.row}>
          <Text style={styles.label}>Status: </Text>
          {enrollment.status}
        </Text>
      </View>

      <TouchableOpacity
        style={styles.button}
        onPress={onCheckPending}
        activeOpacity={0.85}>
        <Text style={styles.buttonText}>Check for pending request</Text>
      </TouchableOpacity>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: spacing.xl,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cardTitle: {
    fontSize: typography.fontSize.xl,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.md,
  },
  row: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.sm,
  },
  label: {
    color: colors.textMuted,
  },
  button: {
    backgroundColor: colors.primary,
    paddingVertical: spacing.lg,
    borderRadius: borderRadius.md,
    alignItems: 'center',
  },
  buttonText: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textOnPrimary,
  },
  errorText: {
    fontSize: typography.fontSize.base,
    color: colors.error,
    padding: spacing.xl,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
