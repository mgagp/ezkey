import React from 'react';
import {createStackNavigator} from '@react-navigation/stack';
import {EnrollmentDetailScreen} from '../screens/EnrollmentDetail';
import {HomeScreen} from '../screens/Home';
import {PendingAuthScreen} from '../screens/PendingAuth';
import {EnrollmentWizardScreen} from '../screens/EnrollmentWizard';
import {EnrollmentScannerScreen} from '../screens/EnrollmentScanner';
import {RootStackParamList} from './types';
import {DiagnosticsScreen} from '../screens/Diagnostics';

const Stack = createStackNavigator<RootStackParamList>();

export const AppNavigator: React.FC = () => (
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
      <Stack.Screen
        name="Diagnostics"
        component={DiagnosticsScreen}
        options={{title: 'Diagnostics'}}
      />
      <Stack.Screen
        name="EnrollmentScanner"
        component={EnrollmentScannerScreen}
        options={{title: 'Scan Enrollment QR'}}
      />
    </Stack.Navigator>
);
