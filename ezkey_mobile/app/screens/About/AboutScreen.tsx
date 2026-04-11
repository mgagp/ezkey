/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import React, {useEffect, useState} from 'react';
import {Text, StyleSheet, ScrollView, TouchableOpacity, Linking} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors, spacing, typography} from '../../config/theme';
import {EzkeyLogo} from '../../components/EzkeyLogo';
import {nativeCrypto} from '../../services/crypto';

const APP_VERSION = '0.0.1';
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
      <Text style={styles.version}>Version {APP_VERSION}</Text>
      <Text style={styles.buildTimestamp} selectable accessibilityLabel="Native app build time UTC">
        {buildTimestampUtc === 'loading'
          ? 'Build (UTC): …'
          : buildTimestampUtc === 'unavailable'
            ? 'Build (UTC): unavailable'
            : `Build (UTC): ${buildTimestampUtc}`}
      </Text>
      <Text style={styles.description}>
        Ezkey is a cryptographic MFA platform, intentionally distinct from FIDO2/WebAuthn and passkey protocols. This
        app lets you enroll devices and approve sign-in requests from your admin console.
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
  version: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.xs,
  },
  buildTimestamp: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.xs,
    paddingHorizontal: spacing.md,
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
