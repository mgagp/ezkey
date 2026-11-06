import React, {useEffect, useState} from 'react';
import {Pressable, ScrollView, Text, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {changeAppLanguage, getLanguagePreference, LanguagePreference} from '../../i18n';
import {SUPPORTED_LOCALES} from '../../services/storage/localeStorage';
import {borderRadius, spacing, typography} from '../../config/theme';
import {createThemedStyles} from '../../theme';

const LANGUAGE_OPTIONS: readonly LanguagePreference[] = ['system', ...SUPPORTED_LOCALES];

const optionLabels: Record<LanguagePreference, string> = {
  system: 'locale.system',
  en: 'locale.english',
  fr: 'locale.french',
};

/**
 * Manual language picker for the app.
 *
 * The choice is saved and applied immediately via i18next; on Android 13+ it is also the
 * system per-app language (Settings → App languages), kept in sync both ways (#1157).
 *
 * @since 2025
 */
export const LanguageScreen: React.FC = () => {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const [selected, setSelected] = useState<LanguagePreference | undefined>();
  const [pending, setPending] = useState<LanguagePreference | undefined>();

  useEffect(() => {
    let active = true;

    getLanguagePreference().then(preference => {
      if (active) {
        setSelected(preference);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  const handleSelect = async (preference: LanguagePreference) => {
    if (pending || preference === selected) {
      return;
    }

    setPending(preference);

    try {
      await changeAppLanguage(preference);
      setSelected(preference);
    } finally {
      setPending(undefined);
    }
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('locale.title')}>
      <Text style={styles.intro}>{t('locale.intro')}</Text>
      <View style={styles.card} accessibilityRole="radiogroup">
        {LANGUAGE_OPTIONS.map((option, index) => {
          const checked = option === selected;
          const disabled = pending != null;

          return (
            <Pressable
              key={option}
              style={({pressed}) => [
                styles.item,
                index === LANGUAGE_OPTIONS.length - 1 && styles.itemLast,
                checked && styles.itemSelected,
                pressed && !disabled && styles.itemPressed,
              ]}
              onPress={() => {
                handleSelect(option).catch(() => {});
              }}
              disabled={disabled}
              accessibilityRole="radio"
              accessibilityState={{checked, disabled}}
              accessibilityLabel={t(optionLabels[option])}>
              <View style={styles.itemContent}>
                <Text style={styles.itemLabel}>{t(optionLabels[option])}</Text>
                <Text
                  style={styles.itemSubtitle}
                  importantForAccessibility="no"
                  accessibilityElementsHidden>
                  {checked ? t('locale.current') : ' '}
                </Text>
              </View>
              <View style={[styles.radioOuter, checked && styles.radioOuterSelected]}>
                {checked ? <View style={styles.radioInner} /> : null}
              </View>
            </Pressable>
          );
        })}
      </View>
    </ScrollView>
  );
};

const useStyles = createThemedStyles(colors => ({
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
  itemSubtitle: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
    marginTop: spacing.xs,
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
}));
