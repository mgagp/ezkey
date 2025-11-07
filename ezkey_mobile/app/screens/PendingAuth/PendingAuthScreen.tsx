import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PendingAuth'>;

export const PendingAuthScreen: React.FC<Props> = ({route}) => {
  const {enrollmentId} = route.params;

  return (
    <View style={styles.container}>
      <Text style={styles.heading}>Pending authentication</Text>
      <Text style={styles.subtitle}>
        Enrollment ID: <Text style={styles.emphasis}>{enrollmentId}</Text>
      </Text>
      <Text style={styles.body}>
        This placeholder will render the challenge request card once the Auth API is connected.
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 12,
    backgroundColor: '#0b0d11',
    justifyContent: 'center',
  },
  heading: {
    fontSize: 24,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  subtitle: {
    fontSize: 16,
    color: '#c2c8d5',
  },
  emphasis: {
    fontWeight: '600',
    color: '#f4f7ff',
  },
  body: {
    fontSize: 14,
    color: '#9aa3b6',
  },
});
