/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React, {useEffect, useState} from 'react';
import {Text, StyleSheet, ScrollView, TouchableOpacity, Linking, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {borderRadius, colors, spacing, typography} from '../../config/theme';
import {EzkeyLogo} from '../../components/EzkeyLogo';
import {nativeCrypto} from '../../services/crypto';
import {APP_VERSION} from '../../config/appInfo';

const EZKEY_SITE_URL = 'https://ezkey.org';

/**
 * App metadata and link to the public Ezkey site (project updates and context).
 *
 * @since 2025
 */
export const AboutScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const [buildTimestampUtc, setBuildTimestampUtc] = useState<string | 'loading' | 'unavailable'>(
    'loading',
  );

  useEffect(() => {
    let cancelled = false;
    nativeCrypto
      .getBuildTimestamp()
      .then(value => {
        const trimmed = value.trim();
        if (!cancelled) {
          setBuildTimestampUtc(trimmed.length > 0 ? trimmed : 'unavailable');
        }
      })
      .catch(() => {
        if (!cancelled) {
          setBuildTimestampUtc('unavailable');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const openSite = () => {
    Linking.openURL(EZKEY_SITE_URL).catch(() => {});
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel="About Ezkey Authenticator">
      <EzkeyLogo size={120} />
      <Text style={styles.title}>Ezkey Authenticator</Text>
      <Text style={styles.tagline}>Cryptographic MFA — backend-first, self-hosted, open source.</Text>
      <View style={styles.metaCard}>
        <View style={styles.metaRow}>
          <Text style={styles.metaLabel}>Version</Text>
          <Text style={styles.metaValue} selectable accessibilityLabel={`App version ${APP_VERSION}`}>
            {APP_VERSION}
          </Text>
        </View>
        <View style={styles.metaRow}>
          <Text style={styles.metaLabel}>Build (UTC)</Text>
          <Text
            style={styles.metaValue}
            selectable
            accessibilityLabel="Native app build time UTC">
            {buildTimestampUtc === 'loading'
              ? '…'
              : buildTimestampUtc === 'unavailable'
                ? 'unavailable'
                : buildTimestampUtc}
          </Text>
        </View>
        <View style={styles.metaRow}>
          <Text style={styles.metaLabel}>License</Text>
          <Text style={styles.metaValue}>MIT — Open Source</Text>
        </View>
      </View>
      <Text style={styles.description}>
        Ezkey lets you enroll trusted devices and approve sign-in requests from your admin console. It is a
        proprietary cryptographic protocol, intentionally distinct from FIDO2/WebAuthn and passkeys, designed for
        operators who want full control over their authentication backend.
      </Text>
      <TouchableOpacity
        style={styles.linkButton}
        onPress={openSite}
        accessibilityRole="link"
        accessibilityLabel="Open Ezkey website"
        accessibilityHint="Opens ezkey.org in the browser">
        <Text style={styles.linkText}>ezkey.org — project site and updates</Text>
      </TouchableOpacity>
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
    alignItems: 'center',
  },
  title: {
    fontSize: typography.fontSize.title,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    textAlign: 'center',
    marginTop: spacing.lg,
  },
  tagline: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    textAlign: 'center',
    marginTop: spacing.xs,
    paddingHorizontal: spacing.md,
    lineHeight: 20,
  },
  metaCard: {
    alignSelf: 'stretch',
    marginTop: spacing.xl,
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
  },
  metaLabel: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    fontWeight: typography.fontWeight.medium,
  },
  metaValue: {
    fontSize: typography.fontSize.base,
    color: colors.textPrimary,
    fontWeight: typography.fontWeight.medium,
  },
  description: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 22,
    marginTop: spacing.xl,
    paddingHorizontal: spacing.sm,
    textAlign: 'center',
    alignSelf: 'stretch',
  },
  linkButton: {
    marginTop: spacing.xl,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    alignItems: 'center',
  },
  linkText: {
    fontSize: typography.fontSize.base,
    color: colors.primaryLight,
    fontWeight: typography.fontWeight.medium,
    textAlign: 'center',
  },
});
