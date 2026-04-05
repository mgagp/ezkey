import React from 'react';
import {createStackNavigator} from '@react-navigation/stack';
import {EnrollmentDetailScreen} from '../screens/EnrollmentDetail';
import {HomeScreen} from '../screens/Home';
import {PendingAuthScreen} from '../screens/PendingAuth';
import {EnrollmentWizardScreen} from '../screens/EnrollmentWizard';
import {RootStackParamList} from './types';
import {DiagnosticsScreen} from '../screens/Diagnostics';
import {DangerZoneScreen} from '../screens/DangerZone';
import {SettingsScreen} from '../screens/Settings';
import {AboutScreen} from '../screens/About';
import {LicensesScreen} from '../screens/Licenses';
import {colors} from '../config/theme';
import {HeaderSettingsButton} from '../components/HeaderSettingsButton';

const Stack = createStackNavigator<RootStackParamList>();

const stackScreenOptions = {
  headerStyle: {backgroundColor: colors.surface},
  headerTintColor: colors.textPrimary,
  headerTitleStyle: {fontWeight: '600' as const, fontSize: 18, color: colors.textPrimary},
  cardStyle: {backgroundColor: colors.background},
};

export const AppNavigator: React.FC = () => (
  <Stack.Navigator screenOptions={stackScreenOptions}>
    <Stack.Screen
      name="Home"
      component={HomeScreen}
      options={({navigation}) => ({
        title: 'Ezkey Authenticator',
        headerBackTitle: 'Back',
        /* React Navigation headerRight API uses a render function; not an inline component type. */
        // eslint-disable-next-line react/no-unstable-nested-components
        headerRight: () => (
          <HeaderSettingsButton onPress={() => navigation.navigate('Settings')} />
        ),
      })}
    />
    <Stack.Screen
      name="EnrollmentDetail"
      component={EnrollmentDetailScreen}
      options={{title: 'Enrollment Detail'}}
    />
    <Stack.Screen
      name="PendingAuth"
      component={PendingAuthScreen}
      options={{title: 'Pending Authentication'}}
    />
    <Stack.Screen
      name="EnrollmentWizard"
      component={EnrollmentWizardScreen}
      options={{title: 'Add Enrollment'}}
    />
    <Stack.Screen name="Settings" component={SettingsScreen} options={{title: 'Settings'}} />
    <Stack.Screen name="About" component={AboutScreen} options={{title: 'About'}} />
    <Stack.Screen name="Licenses" component={LicensesScreen} options={{title: 'Open Source Licenses'}} />
    {__DEV__ ? (
      <Stack.Screen name="Diagnostics" component={DiagnosticsScreen} options={{title: 'Diagnostics'}} />
    ) : null}
    <Stack.Screen name="DangerZone" component={DangerZoneScreen} options={{title: 'Danger Zone'}} />
  </Stack.Navigator>
);
