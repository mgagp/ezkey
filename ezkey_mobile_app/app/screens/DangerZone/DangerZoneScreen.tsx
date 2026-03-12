import React, {useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors, spacing, typography, borderRadius} from '../../config/theme';
import {useEnrollments, useDeleteEnrollment} from '../../hooks/useEnrollments';

export const DangerZoneScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {data: enrollmentsList = [], isLoading} = useEnrollments();
  const deleteEnrollment = useDeleteEnrollment();
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const enrollments = enrollmentsList;

  const onDeletePress = (id: string, integrationName: string) => {
    Alert.alert(
      'Delete enrollment',
      `Remove "${integrationName}" from this device? This action cannot be undone.`,
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            setDeletingId(id);
            try {
              await deleteEnrollment.mutateAsync(id);
            } finally {
              setDeletingId(null);
            }
          },
        },
      ],
    );
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}>
      <Text style={styles.warning}>
        Deleting an enrollment removes it from this device. You will need to
        scan a new QR code to enroll again.
      </Text>
      <View style={styles.list}>
        {isLoading ? (
          <Text style={styles.empty}>Loading…</Text>
        ) : enrollments.length === 0 ? (
          <Text style={styles.empty}>No enrollments to delete.</Text>
        ) : (
          enrollments.map((e) => (
            <View key={e.id} style={styles.card}>
              <Text style={styles.cardTitle}>{e.integrationName}</Text>
              <Text style={styles.cardMeta}>{e.tenantName}</Text>
              <TouchableOpacity
                style={styles.deleteButton}
                onPress={() => onDeletePress(e.id, e.integrationName)}
                disabled={deletingId === e.id}>
                <Text style={styles.deleteButtonText}>
                  {deletingId === e.id ? 'Deleting…' : 'Delete'}
                </Text>
              </TouchableOpacity>
            </View>
          ))
        )}
      </View>
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
  warning: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginBottom: spacing.xl,
    lineHeight: 20,
  },
  list: {
    gap: spacing.md,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
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
  deleteButton: {
    marginTop: spacing.md,
    paddingVertical: spacing.sm,
    alignSelf: 'flex-start',
  },
  deleteButtonText: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.error,
  },
  empty: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
    textAlign: 'center',
    paddingVertical: spacing.xxl,
  },
});
