/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {Linking, ScrollView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {borderRadius, colors, spacing, typography} from '../../config/theme';

const releaseNoteKeyOrder = ['includedItem1', 'includedItem2', 'includedItem3'] as const;
const comingNextKeyOrder = ['comingNextItem1', 'comingNextItem2'] as const;
const noteKeyOrder = ['note1'] as const;

/**
 * Presents the current release notes and limited experimental access information.
 *
 * @since 2025
 */
export const ReleaseNotesScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();

  const openSite = () => {
    Linking.openURL('https://ezkey.org').catch(() => {});
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('releaseNotes.accessibilityLabel')}>
      <View style={styles.heroCard}>
        <Text style={styles.eyebrow}>{t('releaseNotes.eyebrow')}</Text>
        <Text style={styles.title}>{t('releaseNotes.title')}</Text>
        <Text style={styles.intro}>{t('releaseNotes.intro')}</Text>
        <TouchableOpacity
          onPress={openSite}
          accessibilityRole="link"
          accessibilityLabel={t('releaseNotes.siteLinkLabel')}
          accessibilityHint={t('releaseNotes.siteLinkHint')}>
          <Text style={styles.linkText}>{t('releaseNotes.siteLinkText')}</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>{t('releaseNotes.includedTitle')}</Text>
        {releaseNoteKeyOrder.map(key => (
          <View key={key} style={styles.bulletRow}>
            <Text style={styles.bulletMarker}>•</Text>
            <Text style={styles.bulletText}>{t(`releaseNotes.${key}`)}</Text>
          </View>
        ))}
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>{t('releaseNotes.accessTitle')}</Text>
        <Text style={styles.sectionBody}>{t('releaseNotes.accessBody')}</Text>
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>{t('releaseNotes.comingNextTitle')}</Text>
        {comingNextKeyOrder.map(key => (
          <View key={key} style={styles.bulletRow}>
            <Text style={styles.bulletMarker}>•</Text>
            <Text style={styles.bulletText}>{t(`releaseNotes.${key}`)}</Text>
          </View>
        ))}
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>{t('releaseNotes.notesTitle')}</Text>
        {noteKeyOrder.map(key => (
          <View key={key} style={styles.bulletRow}>
            <Text style={styles.bulletMarker}>•</Text>
            <Text style={styles.bulletText}>{t(`releaseNotes.${key}`)}</Text>
          </View>
        ))}
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
    gap: spacing.lg,
  },
  heroCard: {
    backgroundColor: colors.surfaceMuted,
    borderRadius: borderRadius.xl,
    borderWidth: 1,
    borderColor: colors.borderFocus,
    padding: spacing.xl,
    gap: spacing.sm,
  },
  eyebrow: {
    fontSize: typography.fontSize.sm,
    fontWeight: typography.fontWeight.semibold,
    color: colors.warning,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  title: {
    fontSize: typography.fontSize.title,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  intro: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 22,
  },
  linkText: {
    marginTop: spacing.xs,
    fontSize: typography.fontSize.base,
    color: colors.primaryLight,
    fontWeight: typography.fontWeight.medium,
  },
  sectionCard: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.sm,
  },
  sectionTitle: {
    fontSize: typography.fontSize.lg,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  sectionBody: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 21,
  },
  bulletRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
  },
  bulletMarker: {
    fontSize: typography.fontSize.base,
    color: colors.primaryLight,
    lineHeight: 21,
  },
  bulletText: {
    flex: 1,
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 21,
  },
});