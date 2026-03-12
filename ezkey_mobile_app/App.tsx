/**
 * Ezkey Mobile App - Entry component.
 * StatusBar: light-content on dark background; Android uses theme background.
 */

import React from 'react';
import {Platform, StatusBar} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {AppProviders} from './app/providers';
import {colors} from './app/config/theme';

function App(): React.JSX.Element {
  return (
    <SafeAreaProvider>
      <StatusBar
        barStyle="light-content"
        backgroundColor={colors.background}
        translucent={Platform.OS === 'android' ? false : undefined}
      />
      <AppProviders />
    </SafeAreaProvider>
  );
}

export default App;
