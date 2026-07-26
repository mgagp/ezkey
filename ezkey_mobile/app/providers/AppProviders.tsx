import React, {useEffect, useState} from 'react';
import {ActivityIndicator, StyleSheet, View} from 'react-native';
import {NavigationContainer} from '@react-navigation/native';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {AppNavigator} from '../navigation';
import {AppErrorBoundary} from '../components/AppErrorBoundary';
import {UnsupportedOsScreen} from '../screens/UnsupportedOs';
import {colors} from '../config/theme';
import {initializeI18n} from '../i18n';
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

  useEffect(() => {
    let active = true;

    initializeI18n()
      .catch(error => {
        console.warn('[i18n] Failed to initialize, using fallback locale:', error);
      })
      .finally(() => {
        if (active) {
          setReady(true);
        }
      });

    return () => {
      active = false;
    };
  }, []);

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
          <NavigationContainer>
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
