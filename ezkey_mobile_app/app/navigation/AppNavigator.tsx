import React from 'react';
import {TouchableOpacity, Text} from 'react-native';
import {createStackNavigator} from '@react-navigation/stack';
import {RootStackParamList} from './types';
import {HomeScreen} from '../screens/Home';
import {spacing} from '../config/theme';
import {EnrollmentFlowScreen} from '../screens/EnrollmentFlow';
import {EnrollmentDetailScreen} from '../screens/EnrollmentDetail';
import {PendingAuthScreen} from '../screens/PendingAuth';
import {SettingsScreen} from '../screens/Settings';
import {AboutScreen} from '../screens/About';
import {DangerZoneScreen} from '../screens/DangerZone';
import {LicensesScreen} from '../screens/Licenses';
import {colors} from '../config/theme';

const Stack = createStackNavigator<RootStackParamList>();

export const AppNavigator: React.FC = () => (
  <Stack.Navigator
    screenOptions={{
      headerStyle: {backgroundColor: colors.surface},
      headerTintColor: colors.textPrimary,
      headerTitleStyle: {fontWeight: '600', fontSize: 18},
      cardStyle: {backgroundColor: colors.background},
    }}>
    <Stack.Screen
      name="Home"
      component={HomeScreen}
      options={({navigation}) => ({
        title: 'ezkey Authenticator',
        headerRight: () => (
          <TouchableOpacity
            onPress={() => navigation.navigate('Settings')}
            style={{padding: spacing.sm, marginRight: spacing.xs}}
            hitSlop={{top: 12, bottom: 12, left: 12, right: 12}}>
            <Text style={{fontSize: 22, color: colors.textPrimary}}>⚙</Text>
          </TouchableOpacity>
        ),
      })}
    />
    <Stack.Screen
      name="EnrollmentFlow"
      component={EnrollmentFlowScreen}
      options={{title: 'Add Enrollment'}}
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
      name="Settings"
      component={SettingsScreen}
      options={{title: 'Settings'}}
    />
    <Stack.Screen
      name="About"
      component={AboutScreen}
      options={{title: 'About'}}
    />
    <Stack.Screen
      name="DangerZone"
      component={DangerZoneScreen}
      options={{title: 'Danger Zone'}}
    />
    <Stack.Screen
      name="Licenses"
      component={LicensesScreen}
      options={{title: 'Open Source Licenses'}}
    />
  </Stack.Navigator>
);
