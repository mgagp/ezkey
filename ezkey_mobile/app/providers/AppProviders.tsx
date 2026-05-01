import React, {useEffect, useState} from 'react';
import {ActivityIndicator, StyleSheet, View} from 'react-native';
import {NavigationContainer} from '@react-navigation/native';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {AppNavigator} from '../navigation';
import {colors} from '../config/theme';
import {initializeI18n} from '../i18n';

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
    <QueryClientProvider client={queryClient}>
      {ready ? (
        <NavigationContainer>
          <AppNavigator />
        </NavigationContainer>
      ) : (
        <View
          style={styles.loadingContainer}
          accessibilityLabel="Loading application shell">
          <ActivityIndicator color={colors.primaryLight} accessibilityLabel="Loading" />
        </View>
      )}
    </QueryClientProvider>
  );
};

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
