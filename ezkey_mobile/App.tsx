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

const App: React.FC = () => (
  <GestureHandlerRootView style={styles.root}>
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" backgroundColor="#0b0d11" />
      <AppProviders />
    </SafeAreaProvider>
  </GestureHandlerRootView>
);

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#0b0d11',
  },
});

export default App;
