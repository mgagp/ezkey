import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {borderRadius, colors, spacing, typography} from '../config/theme';

type Props = {
  challengeRequiredByPolicy: boolean;
  showLabel?: boolean;
};

export const EnrollmentPolicyBadge: React.FC<Props> = ({
  challengeRequiredByPolicy,
  showLabel = false,
}) => (
  <View style={styles.container}>
    {showLabel ? <Text style={styles.label}>Enrollment policy</Text> : null}
    <Text
      style={[
        styles.badge,
        challengeRequiredByPolicy ? styles.requiredBadge : styles.optionalBadge,
      ]}>
      {challengeRequiredByPolicy ? 'Challenge required' : 'Challenge optional'}
    </Text>
  </View>
);

const styles = StyleSheet.create({
  container: {
    alignItems: 'flex-start',
    gap: spacing.xs + 2,
  },
  label: {
    fontSize: typography.fontSize.xs,
    fontWeight: typography.fontWeight.semibold,
    color: colors.primaryMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  badge: {
    alignSelf: 'flex-start',
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    fontSize: typography.fontSize.xs,
    fontWeight: typography.fontWeight.bold,
    overflow: 'hidden',
  },
  requiredBadge: {
    borderColor: 'rgba(90, 156, 247, 0.45)',
    color: colors.textAccent,
    backgroundColor: 'rgba(90, 156, 247, 0.16)',
  },
  optionalBadge: {
    borderColor: 'rgba(90, 156, 247, 0.35)',
    color: colors.primaryLight,
    backgroundColor: 'rgba(90, 156, 247, 0.08)',
  },
});