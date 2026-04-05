/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React from 'react';
import {View, Text, StyleSheet, ScrollView} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors, spacing, typography, borderRadius} from '../../config/theme';
import thirdPartyData from '../../data/thirdPartyLicenses.json';

type LicenseRow = {
  name: string;
  version: string;
  license: string;
};

const PACKAGES: LicenseRow[] = thirdPartyData.packages;

/**
 * Third-party packages shipped with the app (direct dependencies). Regenerate data with
 * `yarn license:app-data` after dependency changes.
 *
 * @since 2025
 */
export const LicensesScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel="Open source licenses">
      <Text style={styles.intro} accessibilityRole="text">
        This app bundles the following direct npm dependencies. Run yarn license:app-data in the project to refresh this
        list after dependency changes.
      </Text>
      {thirdPartyData.generatedAt ? (
        <Text style={styles.meta} accessibilityLabel={`Data generated at ${thirdPartyData.generatedAt}`}>
          Data snapshot: {thirdPartyData.generatedAt}
        </Text>
      ) : null}
      <View style={styles.list} accessibilityRole="list">
        {PACKAGES.map((item, index) => (
          <View
            key={item.name}
            style={[styles.row, index === PACKAGES.length - 1 && styles.rowLast]}
            accessibilityLabel={`${item.name} version ${item.version}, ${item.license} license`}>
            <View style={styles.rowText}>
              <Text style={styles.name}>{item.name}</Text>
              {item.version ? <Text style={styles.version}>{item.version}</Text> : null}
            </View>
            <Text style={styles.license}>{item.license}</Text>
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
  },
  intro: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    marginBottom: spacing.md,
    lineHeight: 22,
  },
  meta: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginBottom: spacing.xl,
  },
  list: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    gap: spacing.md,
  },
  rowText: {
    flex: 1,
    minWidth: 0,
  },
  name: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.textPrimary,
  },
  version: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
  },
  license: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    flexShrink: 0,
    maxWidth: '38%',
    textAlign: 'right',
  },
  rowLast: {
    borderBottomWidth: 0,
  },
});
