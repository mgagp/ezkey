import React from 'react';
import {Button, StyleSheet, Text, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'EnrollmentWizard'>;

export const EnrollmentWizardScreen: React.FC<Props> = ({navigation}) => {
  return (
    <View style={styles.container}>
      <Text style={styles.heading}>Add enrollment</Text>
      <Text style={styles.body}>
        The wizard will walk through camera permissions, QR capture, and proof exchange once the
        Auth API integration is wired. For now this placeholder documents the flow entry point.
      </Text>
      <Button title="Back" onPress={() => navigation.goBack()} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 16,
    backgroundColor: '#0b0d11',
    justifyContent: 'center',
  },
  heading: {
    fontSize: 24,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  body: {
    fontSize: 16,
    color: '#c2c8d5',
  },
});
