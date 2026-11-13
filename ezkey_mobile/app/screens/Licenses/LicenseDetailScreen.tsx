/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {Linking, ScrollView, Text, TouchableOpacity, View} from 'react-native';
import {StackScreenProps} from '@react-navigation/stack';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {RootStackParamList} from '../../navigation/types';
import {borderRadius, spacing, typography} from '../../config/theme';
import {createThemedStyles} from '../../theme';
import {getLicenseEntry, getLicenseTexts, nativeCoordinates} from './licenseData';

type Props = StackScreenProps<RootStackParamList, 'LicenseDetail'>;

/**
 * One third-party component: version, license, project link, and the full license
 * text bundled with the app (offline). Vendor components show a link to their terms.
 *
 * @since 2026
 */
export const LicenseDetailScreen: React.FC<Props> = ({route}) => {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const entry = getLicenseEntry(route.params.id);

  if (!entry) {
    return (
      <View style={styles.container}>
        <Text style={[styles.body, styles.content]}>{t('licenses.detail.notFound')}</Text>
      </View>
    );
  }

  const texts = getLicenseTexts(entry);
  const coordinates = nativeCoordinates(entry);
  const open = (url: string) => {
    Linking.openURL(url).catch(() => {});
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}>
      <Text style={styles.title} accessibilityRole="header">
        {entry.name}
      </Text>
      {coordinates ? <Text style={styles.coordinates}>{coordinates}</Text> : null}

      <View style={styles.metaCard}>
        <View style={styles.metaRow}>
          <Text style={styles.metaLabel}>{t('licenses.detail.version')}</Text>
          <Text style={styles.metaValue} selectable>
            {entry.version}
          </Text>
        </View>
        <View style={[styles.metaRow, styles.metaRowLast]}>
          <Text style={styles.metaLabel}>{t('licenses.detail.license')}</Text>
          <Text style={styles.metaValue}>{entry.license}</Text>
        </View>
      </View>

      {entry.proprietary ? <Text style={styles.body}>{t('licenses.detail.vendorNote')}</Text> : null}

      {entry.website ? (
        <TouchableOpacity
          onPress={() => open(entry.website!)}
          accessibilityRole="link"
          accessibilityHint={t('licenses.detail.websiteHint')}>
          <Text style={styles.link}>{t('licenses.detail.website')}</Text>
        </TouchableOpacity>
      ) : null}

      {(entry.termsUrls ?? []).map(url => (
        <TouchableOpacity
          key={url}
          onPress={() => open(url)}
          accessibilityRole="link"
          accessibilityHint={t('licenses.detail.termsHint')}>
          <Text style={styles.link}>{t('licenses.detail.terms')}</Text>
        </TouchableOpacity>
      ))}

      {texts.map(text => (
        <View key={text.slice(0, 64)} style={styles.textCard}>
          <Text style={styles.licenseText} selectable>
            {text}
          </Text>
        </View>
      ))}

      {texts.length === 0 && (entry.termsUrls ?? []).length === 0 ? (
        <Text style={styles.body}>{t('licenses.detail.noText')}</Text>
      ) : null}
    </ScrollView>
  );
};

const useStyles = createThemedStyles(colors => ({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: spacing.xl,
    gap: spacing.md,
  },
  title: {
    fontSize: typography.fontSize.title,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  coordinates: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
  },
  metaCard: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    gap: spacing.md,
  },
  metaRowLast: {
    borderBottomWidth: 0,
  },
  metaLabel: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
  },
  metaValue: {
    flexShrink: 1,
    fontSize: typography.fontSize.base,
    color: colors.textPrimary,
    textAlign: 'right',
  },
  body: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 21,
  },
  link: {
    fontSize: typography.fontSize.base,
    color: colors.primaryLight,
    fontWeight: typography.fontWeight.medium,
  },
  textCard: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
  },
  licenseText: {
    fontSize: typography.fontSize.sm,
    color: colors.textSecondary,
    lineHeight: 18,
  },
}));
