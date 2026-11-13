/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React, {useMemo} from 'react';
import {Platform, Pressable, SectionList, Text, View} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {RootStackParamList} from '../../navigation/types';
import {spacing, typography, borderRadius} from '../../config/theme';
import {createThemedStyles} from '../../theme';
import {
  LICENSE_SNAPSHOT_DATE,
  LicenseEntry,
  licenseGroupsFor,
  nativeCoordinates,
} from './licenseData';

type Props = StackScreenProps<RootStackParamList, 'Licenses'>;

type LicenseSection = {
  key: 'app' | 'native' | 'vendor';
  title: string;
  footnote?: string;
  data: LicenseEntry[];
};

/**
 * Third-party components shipped with the app on the running platform: bundled JS,
 * native libraries, and vendor components under their own terms. Each row opens the
 * full license text. Regenerate the data with `yarn license:app-data` (see
 * `scripts/generate-third-party-licenses.mjs`).
 *
 * @since 2025
 */
export const LicensesScreen: React.FC<Props> = ({navigation}) => {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const platform = Platform.OS === 'ios' ? 'ios' : 'android';

  const sections = useMemo<LicenseSection[]>(() => {
    const groups = licenseGroupsFor(platform);
    const platformName = platform === 'ios' ? 'iOS' : 'Android';
    const all: LicenseSection[] = [
      {key: 'app', title: t('licenses.sectionApp'), data: groups.app},
      {
        key: 'native',
        title: t('licenses.sectionNative', {platform: platformName}),
        data: groups.native,
      },
      {
        key: 'vendor',
        title: t('licenses.sectionVendor'),
        footnote: t('licenses.sectionVendorNote'),
        data: groups.vendor,
      },
    ];
    return all.filter(section => section.data.length > 0);
  }, [platform, t]);

  return (
    <SectionList
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('licenses.accessibilityLabel')}
      sections={sections}
      keyExtractor={item => item.id}
      stickySectionHeadersEnabled={false}
      initialNumToRender={20}
      ListHeaderComponent={
        <View>
          <Text style={styles.intro} accessibilityRole="text">
            {t('licenses.intro')}
          </Text>
          {LICENSE_SNAPSHOT_DATE ? (
            <Text
              style={styles.meta}
              accessibilityLabel={t('licenses.generatedAtAccessibility', {
                value: LICENSE_SNAPSHOT_DATE,
              })}>
              {t('licenses.snapshot', {value: LICENSE_SNAPSHOT_DATE})}
            </Text>
          ) : null}
        </View>
      }
      renderSectionHeader={({section}) => (
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle} accessibilityRole="header">
            {section.title}
          </Text>
          {section.footnote ? <Text style={styles.sectionNote}>{section.footnote}</Text> : null}
        </View>
      )}
      renderItem={({item, index, section}) => {
        const coordinates = nativeCoordinates(item);
        const detail = coordinates ? `${coordinates} · ${item.version}` : item.version;
        return (
          <Pressable
            onPress={() => navigation.navigate('LicenseDetail', {id: item.id})}
            accessibilityRole="button"
            accessibilityLabel={t('licenses.rowAccessibility', {
              name: item.name,
              version: item.version,
              license: item.license,
            })}
            accessibilityHint={t('licenses.rowHint')}
            style={({pressed}) => [
              styles.row,
              index === 0 && styles.rowFirst,
              index === section.data.length - 1 && styles.rowLast,
              pressed && styles.rowPressed,
            ]}>
            <View style={styles.rowText}>
              <Text style={styles.name}>{item.name}</Text>
              {detail ? (
                <Text style={styles.version} numberOfLines={1}>
                  {detail}
                </Text>
              ) : null}
            </View>
            <Text style={styles.license}>{item.license}</Text>
          </Pressable>
        );
      }}
    />
  );
};

const useStyles = createThemedStyles(colors => ({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: spacing.xl,
  },
  intro: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.md,
    lineHeight: 22,
  },
  meta: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
  },
  sectionHeader: {
    marginTop: spacing.xl,
    marginBottom: spacing.sm,
    gap: spacing.xs,
  },
  sectionTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  sectionNote: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    lineHeight: 18,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    padding: spacing.lg,
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderLeftWidth: 1,
    borderRightWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  rowFirst: {
    borderTopWidth: 1,
    borderTopLeftRadius: borderRadius.lg,
    borderTopRightRadius: borderRadius.lg,
  },
  rowLast: {
    borderBottomLeftRadius: borderRadius.lg,
    borderBottomRightRadius: borderRadius.lg,
  },
  rowPressed: {
    backgroundColor: colors.surfaceElevated,
  },
  rowText: {
    flex: 1,
    minWidth: 0,
  },
  name: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.textPrimary,
  },
  version: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
  },
  license: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    flexShrink: 0,
    maxWidth: '38%',
    textAlign: 'right',
  },
}));
