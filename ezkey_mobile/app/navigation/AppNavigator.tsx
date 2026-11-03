import React from 'react';
import {StyleSheet} from 'react-native';
import {HeaderBackButton} from '@react-navigation/elements';
import {createStackNavigator, StackNavigationOptions} from '@react-navigation/stack';
import {EnrollmentDetailScreen} from '../screens/EnrollmentDetail';
import {HomeScreen} from '../screens/Home';
import {PendingAuthScreen} from '../screens/PendingAuth';
import {EnrollmentWizardScreen} from '../screens/EnrollmentWizard';
import {RootStackParamList} from './types';
import {DangerZoneScreen} from '../screens/DangerZone';
import {SettingsScreen} from '../screens/Settings';
import {AboutScreen} from '../screens/About';
import {ReleaseNotesScreen} from '../screens/ReleaseNotes';
import {LicenseDetailScreen, LicensesScreen} from '../screens/Licenses';
import {LanguageScreen} from '../screens/Language';
import {SecurityScreen} from '../screens/Security';
import {ComingSoonScreen} from '../screens/ComingSoon';
import {colors} from '../config/theme';
import {HeaderSettingsButton} from '../components/HeaderSettingsButton';
import {useTranslation} from 'react-i18next';

const Stack = createStackNavigator<RootStackParamList>();

type HeaderLeftProps = Parameters<NonNullable<StackNavigationOptions['headerLeft']>>[0];

/** Default back button with a 48 dp touch and accessibility target (#1155). */
const renderHeaderBack = (props: HeaderLeftProps) =>
  props.canGoBack ? <HeaderBackButton {...props} style={styles.headerBack} /> : null;

const baseScreenOptions = {
  headerStyle: {backgroundColor: colors.surface},
  headerTintColor: colors.textPrimary,
  headerTitleStyle: {fontWeight: '600' as const, fontSize: 18, color: colors.textPrimary},
  cardStyle: {backgroundColor: colors.background},
  headerLeft: renderHeaderBack,
};

export const AppNavigator: React.FC = () => {
  const {t} = useTranslation();
  const screenOptions = React.useMemo(
    () => ({...baseScreenOptions, headerBackAccessibilityLabel: t('common.back')}),
    [t],
  );

  return (
    <Stack.Navigator screenOptions={screenOptions}>
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
        name="ReleaseNotes"
        component={ReleaseNotesScreen}
        options={{title: t('navigation.releaseNotes')}}
      />
      <Stack.Screen
        name="ComingSoon"
        component={ComingSoonScreen}
        options={{title: t('navigation.comingSoon')}}
      />
      <Stack.Screen
        name="Licenses"
        component={LicensesScreen}
        options={{title: t('navigation.licenses')}}
      />
      <Stack.Screen
        name="LicenseDetail"
        component={LicenseDetailScreen}
        options={{title: t('navigation.licenseDetail')}}
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
      <Stack.Screen
        name="Security"
        component={SecurityScreen}
        options={{title: t('navigation.security')}}
      />
    </Stack.Navigator>
  );
};

const styles = StyleSheet.create({
  headerBack: {
    minWidth: 48,
    minHeight: 48,
    justifyContent: 'center',
    marginHorizontal: 4,
    marginVertical: 0,
  },
});
