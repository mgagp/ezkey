/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {colors, spacing, typography} from '../../config/theme';
import {ANDROID_PRODUCT_MIN_SDK} from '../../utils/androidOsSupport';

/**
 * Full-app block when the device Android API is below the product floor.
 * Does not mount navigation or Auth API flows.
 *
 * @since 2026
 */
export const UnsupportedOsScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();

  return (
    <View
      style={[
        styles.container,
        {
          paddingTop: insets.top + spacing.xxl,
          paddingBottom: insets.bottom + spacing.xxl,
        },
      ]}
      accessibilityRole="summary"
      accessibilityLabel={t('unsupportedOs.accessibilityLabel')}>
      <Text style={styles.title}>{t('unsupportedOs.title')}</Text>
      <Text style={styles.body}>
        {t('unsupportedOs.body', {minSdk: ANDROID_PRODUCT_MIN_SDK})}
      </Text>
      <Text style={styles.nextStep}>{t('unsupportedOs.nextStep')}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    paddingHorizontal: spacing.xl,
    justifyContent: 'center',
    gap: spacing.lg,
  },
  title: {
    fontSize: typography.fontSize.title,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
  },
  body: {
    fontSize: typography.fontSize.lg,
    color: colors.textSecondary,
    lineHeight: 24,
  },
  nextStep: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
    lineHeight: 22,
  },
});
