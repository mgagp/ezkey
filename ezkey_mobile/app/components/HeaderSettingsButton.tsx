/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {StyleSheet, Text, TouchableOpacity} from 'react-native';
import {useTranslation} from 'react-i18next';
import {colors, spacing} from '../config/theme';

type Props = {
  onPress: () => void;
};

/**
 * Header action that opens Settings (used from stack screen options).
 *
 * @since 2025
 */
export const HeaderSettingsButton: React.FC<Props> = ({onPress}) => {
  const {t} = useTranslation();

  return (
    <TouchableOpacity
      onPress={onPress}
      style={styles.button}
      accessibilityRole="button"
      accessibilityLabel={t('navigation.settings')}
      hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}>
      <Text style={styles.icon}>⚙</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: {
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    marginRight: spacing.xs,
  },
  icon: {
    fontSize: 22,
    color: colors.textPrimary,
  },
});
