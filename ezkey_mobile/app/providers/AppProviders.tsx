import React, {useEffect, useMemo, useState} from 'react';
import {ActivityIndicator, AppState, StyleSheet, View} from 'react-native';
import {DarkTheme, DefaultTheme, NavigationContainer} from '@react-navigation/native';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {AppNavigator} from '../navigation';
import {AppErrorBoundary} from '../components/AppErrorBoundary';
import {UnsupportedOsScreen} from '../screens/UnsupportedOs';
import {initializeI18n, syncAppLanguage} from '../i18n';
import {initializeAppearance} from '../services/appearance/appearance';
import {useTheme} from '../theme';
import {isAndroidOsSupported} from '../utils/androidOsSupport';

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        refetchOnWindowFocus: false,
      },
    },
  });

export const AppProviders: React.FC = () => {
  const [queryClient] = useState(createQueryClient);
  const [ready, setReady] = useState(false);
  const osSupported = isAndroidOsSupported();
  const {scheme, colors} = useTheme();
  const navigationTheme = useMemo(() => {
    const base = scheme === 'light' ? DefaultTheme : DarkTheme;
    return {
      ...base,
      colors: {
        ...base.colors,
        primary: colors.primaryLight,
        background: colors.background,
        card: colors.surface,
        text: colors.textPrimary,
        border: colors.border,
      },
    };
  }, [scheme, colors]);

  useEffect(() => {
    let active = true;

    Promise.all([
      initializeI18n().catch(error => {
        console.warn('[i18n] Failed to initialize, using fallback locale:', error);
      }),
      initializeAppearance().catch(error => {
        console.warn('[appearance] Failed to initialize, following the system theme:', error);
      }),
    ]).finally(() => {
      if (active) {
        setReady(true);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  // The per-app language in system settings can change while the app is in the background (#1157).
  useEffect(() => {
    if (!ready) {
      return undefined;
    }
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        syncAppLanguage().catch(error => {
          console.warn('[i18n] Failed to sync the app language:', error);
        });
      }
    });
    return () => subscription.remove();
  }, [ready]);

  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        {!ready ? (
          <View
            style={styles.loadingContainer}
            accessibilityLabel="Loading application shell">
            <ActivityIndicator color={colors.primaryLight} accessibilityLabel="Loading" />
          </View>
        ) : !osSupported ? (
          <UnsupportedOsScreen />
        ) : (
          <NavigationContainer theme={navigationTheme}>
            <AppNavigator />
          </NavigationContainer>
        )}
      </QueryClientProvider>
    </AppErrorBoundary>
  );
};

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
