/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Lab-only control: when enabled, the Respond request sends authAttemptAccepted opposite to what
 * was signed (proofToken|accepted), so the Auth API rejects with invalid device signature — simulates
 * an attacker altering the wire decision after the user signed. Gated by env.labRespondMitmSimulator.
 */

import React from 'react';
import {StyleSheet, Switch, Text, View} from 'react-native';

type Props = {
  /** When true, next Approve/Deny sends mismatched JSON vs signature. */
  enabled: boolean;
  onEnabledChange: (value: boolean) => void;
};

/**
 * Toggle for outbound Respond MITM simulation (demo / exploration builds only).
 */
export const RespondMitmLabControl: React.FC<Props> = ({enabled, onEnabledChange}) => {
  return (
    <View style={styles.wrap}>
      <View style={styles.row}>
        <Text style={styles.label} accessibilityRole="text">
          Simulate MITM (decision vs signature)
        </Text>
        <Switch
          value={enabled}
          onValueChange={onEnabledChange}
          trackColor={{false: '#3a3f4d', true: 'rgba(245, 166, 35, 0.45)'}}
          thumbColor={enabled ? '#f5a623' : '#9aa3b6'}
          accessibilityLabel="Simulate man in the middle mismatch on respond"
        />
      </View>
      <Text style={styles.hint}>
        When on, the server receives a different approve/deny flag than the value your device signed.
        The backend should reject the request (invalid device signature). Lab builds only.
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  wrap: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(245, 166, 35, 0.25)',
    gap: 8,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  label: {
    flex: 1,
    fontSize: 14,
    fontWeight: '600',
    color: '#e8c170',
  },
  hint: {
    fontSize: 12,
    lineHeight: 17,
    color: '#9aa3b6',
  },
});
