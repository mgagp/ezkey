import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Linking,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors, spacing, typography} from '../../config/theme';
import {EzkeyLogo} from '../../components/EzkeyLogo';

const APP_VERSION = '0.0.1';
const EZKEY_PROJECT_URL = 'https://github.com/ezkey/ezkey';

export const AboutScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const openProject = () => {
    Linking.openURL(EZKEY_PROJECT_URL).catch(() => {});
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}>
      <View style={styles.logoWrap}>
        <EzkeyLogo width={80} height={80} />
      </View>
      <Text style={styles.title}>ezkey Authenticator</Text>
      <Text style={styles.version}>Version {APP_VERSION}</Text>
      <Text style={styles.description}>
        ezkey is an open-source cryptographic MFA platform, intentionally
        distinct from FIDO2/WebAuthn and passkey protocols. This app lets you
        enroll devices and approve sign-in requests from your admin console.
      </Text>
      <TouchableOpacity style={styles.linkButton} onPress={openProject}>
        <Text style={styles.linkText}>Ezkey project on GitHub</Text>
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
  },
  logoWrap: {
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  title: {
    fontSize: typography.fontSize.title,
    fontWeight: typography.fontWeight.semibold,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  version: {
    fontSize: typography.fontSize.base,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.xs,
  },
  description: {
    fontSize: typography.fontSize.base,
    color: colors.textSecondary,
    lineHeight: 22,
    marginTop: spacing.xl,
    paddingHorizontal: spacing.sm,
  },
  linkButton: {
    marginTop: spacing.xl,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  linkText: {
    fontSize: typography.fontSize.base,
    color: colors.primaryLight,
    fontWeight: typography.fontWeight.medium,
  },
});
