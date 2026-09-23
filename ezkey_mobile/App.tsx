/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import React from 'react';
import {StatusBar, StyleSheet} from 'react-native';
import {GestureHandlerRootView} from 'react-native-gesture-handler';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {AppProviders} from './app/providers';
import {colors} from './app/config/theme';

const App: React.FC = () => (
  <GestureHandlerRootView style={styles.root}>
    <SafeAreaProvider>
      {/*
        RN 0.87 removed Android-only StatusBar backgroundColor/translucent props
        (and the underlying native setters). Keep barStyle; root View supplies
        colors.background for the status-bar region under edge-to-edge.
      */}
      <StatusBar barStyle="light-content" />
      <AppProviders />
    </SafeAreaProvider>
  </GestureHandlerRootView>
);

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
});

export default App;
