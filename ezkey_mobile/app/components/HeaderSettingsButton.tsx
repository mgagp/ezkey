/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {Text, TouchableOpacity} from 'react-native';
import {useTranslation} from 'react-i18next';
import {spacing} from '../config/theme';
import {createThemedStyles} from '../theme';

type Props = {
  onPress: () => void;
};

/**
 * Header action that opens Settings (used from stack screen options).
 *
 * @since 2025
 */
export const HeaderSettingsButton: React.FC<Props> = ({onPress}) => {
  const styles = useStyles();
  const {t} = useTranslation();

  return (
    <TouchableOpacity
      onPress={onPress}
      style={styles.button}
      accessibilityRole="button"
      accessibilityLabel={t('navigation.settings')}>
      <Text style={styles.icon} importantForAccessibility="no" accessibilityElementsHidden>
        ⚙
      </Text>
    </TouchableOpacity>
  );
};

const useStyles = createThemedStyles(colors => ({
  button: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.xs,
  },
  icon: {
    fontSize: 22,
    color: colors.textPrimary,
  },
}));
