import React from 'react';
import {View, Text, StyleSheet, ScrollView} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors, spacing, typography, borderRadius} from '../../config/theme';

/**
 * Placeholder list of open-source licenses. In Phase 2 you can replace this
 * with react-native-oss-license or another auto-generated list.
 */
const MOCK_LICENSES: {name: string; license: string}[] = [
  {name: 'React Native', license: 'MIT'},
  {name: 'React', license: 'MIT'},
  {name: 'React Navigation', license: 'MIT'},
  {name: 'Axios', license: 'MIT'},
  {name: 'Zustand', license: 'MIT'},
  {name: 'TanStack Query', license: 'MIT'},
  {name: 'react-native-svg', license: 'MIT'},
  {name: 'react-native-vision-camera', license: 'MIT'},
  {name: 'react-native-keychain', license: 'MIT'},
];

export const LicensesScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[styles.content, {paddingBottom: insets.bottom + spacing.xxl}]}>
      <Text style={styles.intro}>
        This app uses the following open-source software:
      </Text>
      <View style={styles.list}>
        {MOCK_LICENSES.map((item, index) => (
          <View
            key={item.name}
            style={[
              styles.row,
              index === MOCK_LICENSES.length - 1 && styles.rowLast,
            ]}>
            <Text style={styles.name}>{item.name}</Text>
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
    alignItems: 'center',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  name: {
    fontSize: typography.fontSize.base,
    fontWeight: typography.fontWeight.medium,
    color: colors.textPrimary,
  },
  license: {
    fontSize: typography.fontSize.sm,
    color: colors.textMuted,
  },
  rowLast: {
    borderBottomWidth: 0,
  },
});
