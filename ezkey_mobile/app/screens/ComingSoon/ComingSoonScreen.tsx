/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {ScrollView, StyleSheet, Text, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {borderRadius, colors, spacing, typography} from '../../config/theme';

const roadmapSectionKeys = ['auth', 'pinning'] as const;

/**
 * Presents a concise, expectation-setting roadmap for near-term mobile improvements.
 *
 * @since 2025
 */
export const ComingSoonScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('comingSoon.accessibilityLabel')}>
      <View style={styles.heroCard}>
        <Text style={styles.eyebrow}>{t('comingSoon.eyebrow')}</Text>
        <Text style={styles.title}>{t('comingSoon.title')}</Text>
        <Text style={styles.intro}>{t('comingSoon.intro')}</Text>
      </View>

      {roadmapSectionKeys.map(key => (
        <View key={key} style={styles.sectionCard}>
          <Text style={styles.sectionTitle}>{t(`comingSoon.${key}.title`)}</Text>
          <Text style={styles.sectionBody}>{t(`comingSoon.${key}.body`)}</Text>
        </View>
      ))}

      <View style={styles.footerCard}>
        <Text style={styles.footerText}>{t('comingSoon.footer')}</Text>
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
    lineHeight: 22,
  },
  footerCard: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
  },
  footerText: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    lineHeight: 20,
  },
});