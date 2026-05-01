import React from 'react';
import {createStackNavigator} from '@react-navigation/stack';
import {EnrollmentDetailScreen} from '../screens/EnrollmentDetail';
import {HomeScreen} from '../screens/Home';
import {PendingAuthScreen} from '../screens/PendingAuth';
import {EnrollmentWizardScreen} from '../screens/EnrollmentWizard';
import {RootStackParamList} from './types';
import {DangerZoneScreen} from '../screens/DangerZone';
import {SettingsScreen} from '../screens/Settings';
import {AboutScreen} from '../screens/About';
import {LicensesScreen} from '../screens/Licenses';
import {LanguageScreen} from '../screens/Language';
import {colors} from '../config/theme';
import {HeaderSettingsButton} from '../components/HeaderSettingsButton';
import {useTranslation} from 'react-i18next';

const Stack = createStackNavigator<RootStackParamList>();

const stackScreenOptions = {
  headerStyle: {backgroundColor: colors.surface},
  headerTintColor: colors.textPrimary,
  headerTitleStyle: {fontWeight: '600' as const, fontSize: 18, color: colors.textPrimary},
  cardStyle: {backgroundColor: colors.background},
};

export const AppNavigator: React.FC = () => {
  const {t} = useTranslation();

  return (
    <Stack.Navigator screenOptions={stackScreenOptions}>
      <Stack.Screen
        name="Home"
        component={HomeScreen}
        options={({navigation}) => ({
          title: t('navigation.home'),
          headerBackTitle: t('common.back'),
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
        options={{title: t('navigation.enrollmentDetail')}}
      />
      <Stack.Screen
        name="PendingAuth"
        component={PendingAuthScreen}
        options={{title: t('navigation.pendingAuth')}}
      />
      <Stack.Screen
        name="EnrollmentWizard"
        component={EnrollmentWizardScreen}
        options={{title: t('navigation.enrollmentWizard')}}
      />
      <Stack.Screen
        name="Settings"
        component={SettingsScreen}
        options={{title: t('navigation.settings')}}
      />
      <Stack.Screen name="About" component={AboutScreen} options={{title: t('navigation.about')}} />
      <Stack.Screen
        name="Licenses"
        component={LicensesScreen}
        options={{title: t('navigation.licenses')}}
      />
      <Stack.Screen
        name="DangerZone"
        component={DangerZoneScreen}
        options={{title: t('navigation.dangerZone')}}
      />
      <Stack.Screen
        name="Language"
        component={LanguageScreen}
        options={{title: t('navigation.language')}}
      />
    </Stack.Navigator>
  );
};
