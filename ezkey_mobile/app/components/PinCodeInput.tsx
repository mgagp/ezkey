/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: PinCodeInput
 * Description: Reusable numeric PIN entry with individual digit boxes and hidden TextInput overlay.
 *              Used for enrollment challenge (6 digits) and auth challenge (2 digits).
 * @since 2025
 */

import React, {useCallback, useMemo, useRef} from 'react';
import {Pressable, StyleSheet, Text, TextInput, View} from 'react-native';

export type PinCodeInputProps = {
  /** Number of digit boxes to display. */
  length: number;
  value: string;
  onChangeText: (text: string) => void;
  onClearError?: () => void;
  editable?: boolean;
  /** Translated label for the pressable wrapper (accessibility). */
  accessibilityLabel: string;
  /** Translated hint for the pressable wrapper (accessibility). */
  accessibilityHint: string;
  /** Center the digit boxes horizontally. Default: false. */
  centered?: boolean;
  /** Gap between digit boxes in pixels. Default: 12. */
  gap?: number;
};

/**
 * Numeric PIN entry component rendered as a row of individual digit boxes.
 *
 * A hidden full-overlay {@link TextInput} captures keyboard input while the
 * visible boxes display the digits. Supports paste (strips non-digits and
 * truncates to {@link PinCodeInputProps.length}).
 *
 * @since 2025
 */
const PinCodeInput: React.FC<PinCodeInputProps> = ({
  length,
  value,
  onChangeText,
  onClearError,
  editable = true,
  accessibilityLabel,
  accessibilityHint,
  centered = false,
  gap = 12,
}) => {
  const inputRef = useRef<TextInput>(null);
  const digits = value
    .split('')
    .concat(Array(length).fill(''))
    .slice(0, length);

  const dynamicBoxesStyle = useMemo(
    () => ({
      gap,
      justifyContent: centered ? ('center' as const) : ('flex-start' as const),
    }),
    [gap, centered],
  );

  const handleChange = useCallback(
    (text: string) => {
      onClearError?.();
      const digitsOnly = text.replace(/[^0-9]/g, '');
      const next = digitsOnly.length > 1 ? digitsOnly.slice(0, length) : digitsOnly;
      onChangeText(next);
    },
    [length, onChangeText, onClearError],
  );

  return (
    <Pressable
      onPress={() => editable && inputRef.current?.focus()}
      style={styles.container}
      accessibilityLabel={accessibilityLabel}
      accessibilityHint={accessibilityHint}>
      <View style={[styles.boxes, dynamicBoxesStyle]}>
        {digits.map((digit, i) => (
          <View
            key={`digit-${i}`}
            style={[styles.box, digit ? styles.boxFilled : undefined]}>
            <Text style={styles.digit}>{digit || ''}</Text>
          </View>
        ))}
      </View>
      <TextInput
        ref={inputRef}
        value={value}
        onChangeText={handleChange}
        keyboardType="number-pad"
        maxLength={length}
        editable={editable}
        caretHidden
        style={styles.hiddenInput}
      />
    </Pressable>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'relative',
  },
  boxes: {
    flexDirection: 'row',
  },
  box: {
    width: 44,
    height: 52,
    borderRadius: 10,
    backgroundColor: '#151923',
    borderWidth: 3,
    borderColor: 'rgba(54, 115, 223, 0.5)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  boxFilled: {
    borderColor: 'rgba(54, 115, 223, 0.85)',
  },
  digit: {
    fontSize: 28,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  hiddenInput: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    opacity: 0,
    fontSize: 1,
  },
});

export default PinCodeInput;
