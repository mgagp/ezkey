import React from 'react';
import {View, Text, StyleSheet, Pressable, ScrollView} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {RootStackParamList} from '../../navigation/types';
import {colors, spacing, typography, borderRadius} from '../../config/theme';

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
    subtitle: 'Delete enrollments',
  },
  {
    key: 'Licenses',
    label: 'Open Source Licenses',
    subtitle: 'Third-party license listing',
  },
];

export const SettingsScreen: React.FC<Props> = ({navigation}) => {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={{paddingBottom: insets.bottom + spacing.xl}}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Appearance</Text>
        <View style={styles.card}>
          <Text style={styles.row}>Theme: Dark (default)</Text>
          <Text style={styles.hint}>Light theme can be added in a future update.</Text>
        </View>
      </View>

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
              {item.subtitle != null && (
                <Text style={styles.itemSubtitle}>{item.subtitle}</Text>
              )}
              <Text style={styles.chevron}>›</Text>
            </Pressable>
          ))}
        </View>
      </View>
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
  row: {
    fontSize: typography.fontSize.base,
    color: colors.textPrimary,
  },
  hint: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  itemLast: {
    borderBottomWidth: 0,
  },
  itemPressed: {
    opacity: 0.9,
  },
  itemLabel: {
    flex: 1,
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.textPrimary,
  },
  itemSubtitle: {
    flex: 1,
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginLeft: spacing.sm,
  },
  chevron: {
    fontSize: typography.fontSize.xl,
    color: colors.textMuted,
    marginLeft: spacing.sm,
  },
});
