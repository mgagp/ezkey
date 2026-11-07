/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React, {useEffect, useState} from 'react';
import {Pressable, ScrollView, Text, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {
  APPEARANCE_PREFERENCES,
  AppearancePreference,
  appearanceStorage,
  DEFAULT_APPEARANCE,
} from '../../services/storage/appearanceStorage';
import {changeAppearance} from '../../services/appearance/appearance';
import {borderRadius, spacing, typography} from '../../config/theme';
import {createThemedStyles} from '../../theme';

const appearanceLabels: Record<AppearancePreference, string> = {
  system: 'appearance.system',
  light: 'appearance.light',
  dark: 'appearance.dark',
};

/**
 * Light / Dark / System default picker (#1156). The choice is saved and applied immediately.
 *
 * @since 2026
 */
export const AppearanceScreen: React.FC = () => {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const [selected, setSelected] = useState<AppearancePreference>(DEFAULT_APPEARANCE);

  useEffect(() => {
    let active = true;

    appearanceStorage.getAppearance().then(value => {
      if (active) {
        setSelected(value);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  const handleSelect = (preference: AppearancePreference) => {
    if (preference === selected) {
      return;
    }
    setSelected(preference);
    changeAppearance(preference).catch(() => {});
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('appearance.title')}>
      <Text style={styles.intro}>{t('appearance.intro')}</Text>
      <View style={styles.card} accessibilityRole="radiogroup">
        {APPEARANCE_PREFERENCES.map((preference, index) => {
          const checked = preference === selected;

          return (
            <Pressable
              key={preference}
              style={({pressed}) => [
                styles.item,
                index === APPEARANCE_PREFERENCES.length - 1 && styles.itemLast,
                checked && styles.itemSelected,
                pressed && styles.itemPressed,
              ]}
              onPress={() => handleSelect(preference)}
              accessibilityRole="radio"
              accessibilityState={{checked}}
              accessibilityLabel={t(appearanceLabels[preference])}
              testID={`ezkey.appearance.${preference}`}>
              <View style={styles.itemContent}>
                <Text style={styles.itemLabel}>{t(appearanceLabels[preference])}</Text>
                <Text
                  style={styles.itemSubtitle}
                  importantForAccessibility="no"
                  accessibilityElementsHidden>
                  {checked ? t('appearance.current') : ' '}
                </Text>
              </View>
              <View style={[styles.radioOuter, checked && styles.radioOuterSelected]}>
                {checked ? <View style={styles.radioInner} /> : null}
              </View>
            </Pressable>
          );
        })}
      </View>
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
  },
  intro: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 22,
    marginBottom: spacing.lg,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  itemLast: {
    borderBottomWidth: 0,
  },
  itemSelected: {
    backgroundColor: colors.surfaceElevated,
  },
  itemPressed: {
    opacity: 0.9,
  },
  itemContent: {
    flex: 1,
    paddingRight: spacing.lg,
  },
  itemLabel: {
    fontSize: typography.fontSize.base,
    color: colors.textPrimary,
    fontWeight: typography.fontWeight.medium,
  },
  itemSubtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
    minHeight: 18,
  },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.borderControl,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOuterSelected: {
    borderColor: colors.primaryLight,
  },
  radioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.primaryLight,
  },
}));
