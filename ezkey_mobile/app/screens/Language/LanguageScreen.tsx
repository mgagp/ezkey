import React, {useEffect, useState} from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useTranslation} from 'react-i18next';
import {changeAppLanguage, i18n, SupportedLocale} from '../../i18n';
import {
  DEFAULT_LOCALE,
  localeStorage,
  normalizeLocale,
  SUPPORTED_LOCALES,
} from '../../services/storage/localeStorage';
import {borderRadius, colors, spacing, typography} from '../../config/theme';

const localeLabels: Record<SupportedLocale, string> = {
  en: 'locale.english',
  fr: 'locale.french',
};

/**
 * Manual language picker for the app.
 *
 * The selected locale is saved and applied immediately via i18next.
 *
 * @since 2025
 */
export const LanguageScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {t} = useTranslation();
  const [selectedLocale, setSelectedLocale] = useState<SupportedLocale>(
    () => normalizeLocale(i18n.resolvedLanguage ?? i18n.language ?? DEFAULT_LOCALE),
  );
  const [pendingLocale, setPendingLocale] = useState<SupportedLocale | undefined>();

  useEffect(() => {
    let active = true;

    localeStorage.getLocale().then(locale => {
      if (active) {
        setSelectedLocale(locale);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  const handleSelect = async (locale: SupportedLocale) => {
    if (pendingLocale || locale === selectedLocale) {
      return;
    }

    setPendingLocale(locale);

    try {
      const nextLocale = await changeAppLanguage(locale);
      setSelectedLocale(nextLocale);
    } finally {
      setPendingLocale(undefined);
    }
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}
      accessibilityLabel={t('locale.title')}>
      <Text style={styles.intro}>{t('locale.intro')}</Text>
      <View style={styles.card}>
        {SUPPORTED_LOCALES.map((locale, index) => {
          const selected = locale === selectedLocale;
          const disabled = pendingLocale != null;

          return (
            <Pressable
              key={locale}
              style={({pressed}) => [
                styles.item,
                index === SUPPORTED_LOCALES.length - 1 && styles.itemLast,
                selected && styles.itemSelected,
                pressed && !disabled && styles.itemPressed,
              ]}
              onPress={() => {
                handleSelect(locale).catch(() => {});
              }}
              disabled={disabled}
              accessibilityRole="button"
              accessibilityState={{selected, disabled}}
              accessibilityLabel={t(localeLabels[locale])}>
              <View style={styles.itemContent}>
                <Text style={styles.itemLabel}>{t(localeLabels[locale])}</Text>
                <Text style={styles.itemSubtitle}>
                  {selected ? t('locale.current') : ' '}
                </Text>
              </View>
              <View style={[styles.radioOuter, selected && styles.radioOuterSelected]}>
                {selected ? <View style={styles.radioInner} /> : null}
              </View>
            </Pressable>
          );
        })}
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
});
