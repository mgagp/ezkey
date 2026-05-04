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
import {useTranslation} from 'react-i18next';
import {RootStackParamList} from '../../navigation/types';
import {borderRadius, colors, spacing, typography} from '../../config/theme';
import {APP_DISPLAY_NAME, APP_VERSION} from '../../config/appInfo';
import {
  DEFAULT_SECURITY_LEVEL,
  normalizeSecurityLevel,
  securityPreferenceStorage,
  SecurityLevel,
} from '../../services/storage/securityPreferenceStorage';

type Props = StackScreenProps<RootStackParamList, 'Settings'>;

type SettingsScreenName =
  | 'About'
  | 'ReleaseNotes'
  | 'DangerZone'
  | 'Licenses'
  | 'Language'
  | 'Security';

type SettingsItem = {
  key: SettingsScreenName;
  label: string;
  subtitle?: string;
};

/**
 * Hub for secondary actions: about, licenses, and destructive operations.
 *
 * @since 2025
 */
export const SettingsScreen: React.FC<Props> = ({navigation}) => {
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const [securityLevel, setSecurityLevel] = React.useState<SecurityLevel>(
    DEFAULT_SECURITY_LEVEL,
  );

  React.useEffect(() => {
    let active = true;

    securityPreferenceStorage.getSecurityLevel().then(value => {
      if (active) {
        setSecurityLevel(normalizeSecurityLevel(value));
      }
    });

    return () => {
      active = false;
    };
  }, []);

  const settingsItems: SettingsItem[] = [
    {
      key: 'About',
      label: t('settings.aboutLabel'),
      subtitle: t('settings.aboutSubtitle'),
    },
    {
      key: 'ReleaseNotes',
      label: t('settings.releaseNotesLabel'),
      subtitle: t('settings.releaseNotesSubtitle'),
    },
    {
      key: 'Language',
      label: t('settings.languageLabel'),
      subtitle: t('settings.languageSubtitle'),
    },
    {
      key: 'Security',
      label: t('settings.securityLabel'),
      subtitle:
        securityLevel === 'confirm-before-approvals'
          ? t('settings.securitySubtitleProtected')
          : t('settings.securitySubtitleStandard'),
    },
    {
      key: 'DangerZone',
      label: t('settings.dangerZoneLabel'),
      subtitle: t('settings.dangerZoneSubtitle'),
    },
    {
      key: 'Licenses',
      label: t('settings.licensesLabel'),
      subtitle: t('settings.licensesSubtitle'),
    },
  ];

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={{paddingBottom: insets.bottom + spacing.xl}}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>{t('settings.general')}</Text>
        <View style={styles.card}>
          {settingsItems.map((item, index) => (
            <Pressable
              key={item.key}
              style={({pressed}) => [
                styles.item,
                index === settingsItems.length - 1 && styles.itemLast,
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
