/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {View, Text, StyleSheet, Pressable, ScrollView} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {RootStackParamList} from '../../navigation/types';
import {borderRadius, colors, spacing, typography} from '../../config/theme';
import {APP_DISPLAY_NAME, APP_VERSION} from '../../config/appInfo';

type Props = StackScreenProps<RootStackParamList, 'Settings'>;

type SettingsScreenName = 'About' | 'DangerZone' | 'Licenses';

type SettingsItem = {
  key: SettingsScreenName;
  label: string;
  subtitle?: string;
};

const SETTINGS_ITEMS: SettingsItem[] = [
  {key: 'About', label: 'About', subtitle: 'App version and project info'},
  {
    key: 'DangerZone',
    label: 'Danger Zone',
    subtitle: 'Delete enrollments or clear all data',
  },
  {
    key: 'Licenses',
    label: 'Open Source Licenses',
    subtitle: 'Third-party license listing',
  },
];

/**
 * Hub for secondary actions: about, licenses, and destructive operations.
 *
 * @since 2025
 */
export const SettingsScreen: React.FC<Props> = ({navigation}) => {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={{paddingBottom: insets.bottom + spacing.xl}}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>General</Text>
        <View style={styles.card}>
          {SETTINGS_ITEMS.map((item, index) => (
            <Pressable
              key={item.key}
              style={({pressed}) => [
                styles.item,
                index === SETTINGS_ITEMS.length - 1 && styles.itemLast,
                pressed && styles.itemPressed,
              ]}
              onPress={() => navigation.navigate(item.key)}>
              <Text style={styles.itemLabel}>{item.label}</Text>
              {item.subtitle != null && <Text style={styles.itemSubtitle}>{item.subtitle}</Text>}
              <Text style={styles.chevron}>›</Text>
            </Pressable>
          ))}
        </View>
      </View>
      <Text
        style={styles.versionFooter}
        accessibilityRole="text"
        accessibilityLabel={`${APP_DISPLAY_NAME} version ${APP_VERSION}`}>
        {APP_DISPLAY_NAME} v{APP_VERSION}
      </Text>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    padding: spacing.xl,
  },
  section: {
    marginBottom: spacing.xl,
  },
  sectionTitle: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.semibold,
    color: colors.primaryMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.sm,
    marginLeft: spacing.xs,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  item: {
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingRight: spacing.xxl + spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    position: 'relative',
  },
  itemLast: {
    borderBottomWidth: 0,
  },
  itemPressed: {
    opacity: 0.85,
    backgroundColor: colors.surfaceElevated,
  },
  itemLabel: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.textPrimary,
  },
  itemSubtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
    paddingRight: spacing.lg,
  },
  chevron: {
    position: 'absolute',
    right: spacing.lg,
    top: '50%',
    marginTop: -12,
    fontSize: 22,
    color: colors.textMuted,
  },
  versionFooter: {
    marginTop: spacing.xl,
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    textAlign: 'center',
    opacity: 0.7,
  },
});
