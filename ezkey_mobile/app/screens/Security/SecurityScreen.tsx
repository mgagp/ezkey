import React, {useEffect, useState} from 'react';
import {Pressable, ScrollView, StyleSheet, Text, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {
  requiresAuthenticationForSecurityPreferenceChange,
} from '../../services/security/approvalRequirement';
import {
  DEFAULT_SECURITY_LEVEL,
  normalizeSecurityLevel,
  securityPreferenceStorage,
  SECURITY_LEVELS,
  SecurityLevel,
} from '../../services/storage/securityPreferenceStorage';
import {borderRadius, colors, spacing, typography} from '../../config/theme';
import {cryptoService} from '../../services/crypto';

const securityLevelTranslationKeys: Record<
  SecurityLevel,
  {title: string; body: string}
> = {
  standard: {
    title: 'security.standardTitle',
    body: 'security.standardBody',
  },
  'confirm-before-approvals': {
    title: 'security.confirmationTitle',
    body: 'security.confirmationBody',
  },
};

export const SecurityScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const [selectedLevel, setSelectedLevel] = useState<SecurityLevel>(DEFAULT_SECURITY_LEVEL);
  const [pendingLevel, setPendingLevel] = useState<SecurityLevel | undefined>();
  const [protectedSigningAvailable, setProtectedSigningAvailable] = useState(false);
  const [changeError, setChangeError] = useState<string | undefined>();

  useEffect(() => {
    let active = true;

    securityPreferenceStorage.getSecurityLevel().then(level => {
      if (active) {
        setSelectedLevel(normalizeSecurityLevel(level));
      }
    });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    cryptoService.canUseProtectedSigning().then(supported => {
      if (active) {
        setProtectedSigningAvailable(supported);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  const handleSelect = async (level: SecurityLevel) => {
    if (pendingLevel || level === selectedLevel) {
      return;
    }

    setPendingLevel(level);
    setChangeError(undefined);

    try {
      const requiresAuthentication = requiresAuthenticationForSecurityPreferenceChange({
        currentSecurityPreference: selectedLevel,
        nextSecurityPreference: level,
      });
      if (requiresAuthentication && protectedSigningAvailable) {
        await cryptoService.authenticateSecurityPreferenceDowngrade();
      }
      await securityPreferenceStorage.setSecurityLevel(level);
      setSelectedLevel(level);
    } catch (error) {
      const nativeCode =
        typeof error === 'object' && error != null && 'code' in error && typeof error.code === 'string'
          ? error.code
          : undefined;
      if (nativeCode === 'EZK_AUTH_CANCELLED') {
        setChangeError(t('security.settingChangeCancelled'));
        return;
      }
      if (nativeCode === 'EZK_AUTH_UNAVAILABLE') {
        setChangeError(t('security.settingChangeUnavailable'));
        return;
      }

      setChangeError(t('security.settingChangeFailed'));
    } finally {
      setPendingLevel(undefined);
    }
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('security.title')}>
      <Text style={styles.intro}>{t('security.intro')}</Text>
      <View style={styles.card}>
        {SECURITY_LEVELS.map((level, index) => {
          const selected = level === selectedLevel;
          const unsupported =
            level === 'confirm-before-approvals' && !protectedSigningAvailable;
          const disabled = pendingLevel != null || unsupported;
          const copy = securityLevelTranslationKeys[level];

          return (
            <Pressable
              key={level}
              style={({pressed}) => [
                styles.item,
                index === SECURITY_LEVELS.length - 1 && styles.itemLast,
                selected && styles.itemSelected,
                pressed && !disabled && styles.itemPressed,
              ]}
              onPress={() => {
                handleSelect(level).catch(() => {});
              }}
              disabled={disabled}
              accessibilityRole="button"
              accessibilityState={{selected, disabled}}
              accessibilityLabel={t(copy.title)}>
              <View style={styles.itemContent}>
                <Text style={styles.itemLabel}>{t(copy.title)}</Text>
                <Text style={styles.itemBody}>{t(copy.body)}</Text>
                <Text style={styles.itemSubtitle}>{selected ? t('security.current') : ' '}</Text>
              </View>
              <View style={[styles.radioOuter, selected && styles.radioOuterSelected]}>
                {selected ? <View style={styles.radioInner} /> : null}
              </View>
            </Pressable>
          );
        })}
      </View>

      {!protectedSigningAvailable ? (
        <View style={styles.infoCallout}>
          <Text style={styles.infoTitle}>{t('security.unavailableTitle')}</Text>
          <Text style={styles.infoBody}>{t('security.unavailableBody')}</Text>
        </View>
      ) : null}

      {changeError ? (
        <View style={styles.errorCallout}>
          <Text style={styles.errorBody}>{changeError}</Text>
        </View>
      ) : null}

      <View style={styles.futureOnlyCallout}>
        <Text style={styles.futureOnlyEyebrow}>{t('security.preferenceNoteEyebrow')}</Text>
        <Text style={styles.futureOnlyTitle}>{t('security.preferenceNoteTitle')}</Text>
        <Text style={styles.futureOnlyBody}>{t('security.preferenceNoteBody')}</Text>
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
  itemBody: {
    fontSize: typography.fontSize.sm,
    color: colors.textSecondary,
    lineHeight: 20,
    marginTop: spacing.xs,
  },
  itemSubtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.sm,
    minHeight: 18,
  },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.border,
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
  infoCallout: {
    marginTop: spacing.lg,
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
  },
  infoTitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textPrimary,
    fontWeight: typography.fontWeight.semibold,
    marginBottom: spacing.xs,
  },
  infoBody: {
    fontSize: typography.fontSize.sm,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  errorCallout: {
    marginTop: spacing.lg,
    backgroundColor: colors.surface,
    borderRadius: borderRadius.lg,
    borderWidth: 1,
    borderColor: colors.error ?? colors.border,
    padding: spacing.lg,
  },
  errorBody: {
    fontSize: typography.fontSize.sm,
    color: colors.error ?? colors.textPrimary,
    lineHeight: 20,
  },
  futureOnlyCallout: {
    marginTop: spacing.lg,
    backgroundColor: colors.surfaceElevated,
    borderRadius: borderRadius.lg,
    borderLeftWidth: 4,
    borderLeftColor: colors.primaryLight,
    padding: spacing.lg,
  },
  futureOnlyEyebrow: {
    fontSize: typography.fontSize.xs,
    color: colors.primaryLight,
    fontWeight: typography.fontWeight.semibold,
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  futureOnlyTitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textPrimary,
    fontWeight: typography.fontWeight.semibold,
    marginBottom: spacing.xs,
  },
  futureOnlyBody: {
    fontSize: typography.fontSize.sm,
    color: colors.textSecondary,
    lineHeight: 20,
  },
});