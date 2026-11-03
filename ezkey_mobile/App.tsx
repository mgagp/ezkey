/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import React from 'react';
import {StatusBar} from 'react-native';
import {GestureHandlerRootView} from 'react-native-gesture-handler';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {AppProviders} from './app/providers';
import {createThemedStyles, ThemeProvider, useTheme} from './app/theme';

const useStyles = createThemedStyles(colors => ({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
}));

const ThemedRoot: React.FC = () => {
  const {scheme} = useTheme();
  const styles = useStyles();
  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        {/*
          RN 0.87 removed Android-only StatusBar backgroundColor/translucent props
          (and the underlying native setters). Keep barStyle; root View supplies
          colors.background for the status-bar region under edge-to-edge.
        */}
        <StatusBar barStyle={scheme === 'light' ? 'dark-content' : 'light-content'} />
        <AppProviders />
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
};

const App: React.FC = () => (
  <ThemeProvider>
    <ThemedRoot />
  </ThemeProvider>
);

export default App;
