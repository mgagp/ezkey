import React from 'react';
import {createStackNavigator} from '@react-navigation/stack';
import {EnrollmentDetailScreen} from '../screens/EnrollmentDetail';
import {HomeScreen} from '../screens/Home';
import {PendingAuthScreen} from '../screens/PendingAuth';
import {EnrollmentWizardScreen} from '../screens/EnrollmentWizard';
import {RootStackParamList} from './types';

const Stack = createStackNavigator<RootStackParamList>();

export const AppNavigator: React.FC = () => {
  if (__DEV__) {
    // eslint-disable-next-line no-console
    console.log('Navigator screens', {
      HomeScreen,
      EnrollmentDetailScreen,
      PendingAuthScreen,
      EnrollmentWizardScreen,
    });
  }

  return (
    <Stack.Navigator>
      <Stack.Screen
        name="Home"
        component={HomeScreen}
        options={{title: 'Your Enrollments'}}
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
    </Stack.Navigator>
  );
};
