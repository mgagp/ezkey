/**
 * 6-digit challenge code input for enrollment verification.
 */

import React, {useState, useRef} from 'react';
import {
  View,
  TextInput,
  StyleSheet,
  NativeSyntheticEvent,
  TextInputKeyPressEventData,
} from 'react-native';
import {colors, spacing, typography, borderRadius} from '../config/theme';

export type ChallengeInputProps = {
  value: string;
  onChange: (value: string) => void;
  digitCount?: number;
  editable?: boolean;
  autoFocus?: boolean;
  accessibilityLabel?: string;
};

export const ChallengeInput: React.FC<ChallengeInputProps> = ({
  value,
  onChange,
  digitCount = 6,
  editable = true,
  autoFocus = false,
  accessibilityLabel = 'Challenge code',
}) => {
  const refs = useRef<(TextInput | null)[]>([]);
  const digits = value.padEnd(digitCount, ' ').split('').slice(0, digitCount);

  const handleChange = (index: number, char: string) => {
    const digit = char.replace(/\D/g, '').slice(-1);
    const next = value.split('');
    next[index] = digit;
    const newValue = next.join('').slice(0, digitCount);
    onChange(newValue);
    if (digit && index < digitCount - 1) {
      refs.current[index + 1]?.focus();
    }
  };

  const handleKeyPress = (
    index: number,
    e: NativeSyntheticEvent<TextInputKeyPressEventData>,
  ) => {
    if (e.nativeEvent.key === 'Backspace' && !digits[index] && index > 0) {
      refs.current[index - 1]?.focus();
      const next = value.split('');
      next[index - 1] = '';
      onChange(next.join('').slice(0, digitCount));
    }
  };

  return (
    <View style={styles.container}>
      {Array.from({length: digitCount}, (_, i) => (
        <TextInput
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          style={[styles.digit, !editable && styles.digitDisabled]}
          value={digits[i]}
          onChangeText={(char) => handleChange(i, char)}
          onKeyPress={(e) => handleKeyPress(i, e)}
          keyboardType="number-pad"
          maxLength={1}
          editable={editable}
          selectTextOnFocus
          autoFocus={autoFocus && i === 0}
          accessibilityLabel={`${accessibilityLabel} digit ${i + 1}`}
        />
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
  },
  digit: {
    width: 44,
    height: 52,
    borderWidth: 2,
    borderColor: colors.borderStrong,
    borderRadius: borderRadius.md,
    backgroundColor: colors.surface,
    color: colors.textPrimary,
    fontSize: typography.fontSize.xxl,
    fontWeight: typography.fontWeight.semibold,
    textAlign: 'center',
    padding: 0,
  },
  digitDisabled: {
    opacity: 0.7,
  },
});
