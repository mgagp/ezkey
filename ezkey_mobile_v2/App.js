/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: App
 * Description: Main application component for Ezkey Mobile V2
 */

import React, { useState, useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { 
  StyleSheet, 
  View, 
  ScrollView, 
  Text, 
  TextInput, 
  TouchableOpacity,
  Image,
  Alert,
  ActivityIndicator
} from 'react-native';
import { SafeAreaView } from 'react-native';
import ExpoCryptoNative from './modules/expo-crypto-native';
import * as Crypto from 'expo-crypto';
import AuthApiService from './src/services/AuthApiService';
import StorageService from './src/storage/StorageService';

export default function App() {
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [moduleReady, setModuleReady] = useState(false);
  
  // Step 1 - Enrollment
  const [enrollmentId, setEnrollmentId] = useState('');
  const [enrollmentProofToken, setEnrollmentProofToken] = useState('');
  const [enrollmentData, setEnrollmentData] = useState(null);
  const [challengeCode, setChallengeCode] = useState('');
  const [enrollmentComplete, setEnrollmentComplete] = useState(false);
  
  // Step 2 - Summary
  const [summary, setSummary] = useState([]);
  
  // Step 3 - Auth Attempts
  const [pendingAuth, setPendingAuth] = useState(null);
  const [authChallengeResponse, setAuthChallengeResponse] = useState('');

  useEffect(() => {
    // Check if native module is available
    console.log('Checking ExpoCryptoNative module...');
    if (ExpoCryptoNative && typeof ExpoCryptoNative.generateRsaKeyPair === 'function') {
      console.log('✅ ExpoCryptoNative module is ready');
      setModuleReady(true);
      checkExistingEnrollments();
    } else {
      console.error('❌ ExpoCryptoNative module NOT available');
      Alert.alert(
        'Module Error',
        'Crypto module not loaded. Please rebuild the app with: npx expo run:android'
      );
    }
  }, []);

  const checkExistingEnrollments = async () => {
    const enrollments = await StorageService.getAllEnrollments();
    if (enrollments.length > 0) {
      const latest = enrollments[0];
      if (latest.verified) {
        setEnrollmentData(latest);
        setEnrollmentComplete(true);
        setSummary([
          `✓ Enrolled as: ${latest.enrollmentName}`,
          `✓ Integration: ${latest.integrationName}`,
          `✓ Enrollment ID: ${latest.enrollmentId}`,
          `✓ Ready to authenticate`
        ]);
        setStep(2);
      }
    }
  };

  // Step 1: Enrollment Bind
  const handleEnrollmentBind = async () => {
    if (!enrollmentId || !enrollmentProofToken) {
      Alert.alert('Error', 'Please enter both Enrollment ID and Proof Token');
      return;
    }

    setLoading(true);
    try {
      // Call bind API
      const bindResponse = await AuthApiService.enrollmentBind({
        enrollmentId: parseInt(enrollmentId),
        enrollmentProofToken: enrollmentProofToken.trim(),
        language: 'en'
      });

      // Debug: Verify module is loaded
      console.log('ExpoCryptoNative:', ExpoCryptoNative);
      console.log('Available methods:', Object.keys(ExpoCryptoNative || {}));

      // Generate device keys
      console.log('Calling generateRsaKeyPair...');
      const keyPair = ExpoCryptoNative.generateRsaKeyPair(2048);
      console.log('KeyPair generated:', keyPair ? 'SUCCESS' : 'NULL');
      console.log('KeyPair keys:', keyPair ? Object.keys(keyPair) : 'N/A');

      // Store enrollment data
      const enrollment = {
        enrollmentId: bindResponse.enrollmentId,
        enrollmentName: bindResponse.enrollmentName,
        enrollmentProofToken: bindResponse.enrollmentProofToken,
        integrationName: bindResponse.integrationName,
        integrationDescription: bindResponse.integrationDescription,
        integrationLogo: bindResponse.integrationLogo,
        integrationPublicKey: bindResponse.integrationPublicKey,
        devicePublicKey: keyPair.publicKey,
        devicePrivateKey: keyPair.privateKey,
        verified: false,
        createdAt: new Date().toISOString()
      };

      await StorageService.saveEnrollment(enrollment);
      setEnrollmentData(enrollment);

      Alert.alert(
        'Bind Successful',
        `Binding successful for ${bindResponse.integrationName}. Please enter the challenge code to verify.`
      );
    } catch (error) {
      Alert.alert('Error', error.message || 'Enrollment bind failed');
    } finally {
      setLoading(false);
    }
  };

  // Step 1: Enrollment Verify
  const handleEnrollmentVerify = async () => {
    if (!challengeCode) {
      Alert.alert('Error', 'Please enter the challenge code');
      return;
    }

    setLoading(true);
    try {
      // Debug: Check enrollmentData
      console.log('enrollmentData:', enrollmentData ? 'EXISTS' : 'NULL');
      console.log('enrollmentData keys:', enrollmentData ? Object.keys(enrollmentData) : 'N/A');
      console.log('Has devicePrivateKey:', enrollmentData?.devicePrivateKey ? 'YES' : 'NO');

      // Sign the enrollment proof token
      const enrollmentProofTokenSigned = ExpoCryptoNative.generateSignature(
        enrollmentData.enrollmentProofToken,
        enrollmentData.devicePrivateKey
      );

      // Call verify API
      const verifyResponse = await AuthApiService.enrollmentVerify({
        enrollmentId: enrollmentData.enrollmentId,
        challengeResponse: parseInt(challengeCode),
        devicePublicKey: enrollmentData.devicePublicKey,
        enrollmentProofTokenSigned
      });

      if (verifyResponse.active) {
        // Update enrollment as verified
        const updatedEnrollment = { ...enrollmentData, verified: true };
        await StorageService.saveEnrollment(updatedEnrollment);
        setEnrollmentData(updatedEnrollment);
        setEnrollmentComplete(true);

        // Build summary
        setSummary([
          `✓ Enrolled as: ${enrollmentData.enrollmentName}`,
          `✓ Integration: ${enrollmentData.integrationName}`,
          `✓ Enrollment ID: ${enrollmentData.enrollmentId}`,
          `✓ Ready to authenticate`
        ]);

        Alert.alert('Success', 'Enrollment verified successfully!', [
          { text: 'Continue', onPress: () => setStep(2) }
        ]);
      } else {
        Alert.alert('Error', 'Enrollment verification failed');
      }
    } catch (error) {
      Alert.alert('Error', error.message || 'Enrollment verification failed');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Proceed to Auth Check
  const handleProceedToAuth = () => {
    setStep(3);
  };

  // Step 3: Check for pending auth attempts
  const handleCheckPendingAuth = async () => {
    if (!enrollmentData) {
      Alert.alert('Error', 'No enrollment found');
      return;
    }

    setLoading(true);
    try {
      // Generate device proof token
      const deviceProofToken = ExpoCryptoNative.generateProofToken();
      
      // Sign the device proof token
      const deviceProofTokenSigned = ExpoCryptoNative.generateSignature(
        deviceProofToken,
        enrollmentData.devicePrivateKey
      );

      // Check for pending auth
      const pending = await AuthApiService.checkPendingAuth({
        enrollmentId: enrollmentData.enrollmentId,
        enrollmentProofToken: enrollmentData.enrollmentProofToken,
        deviceProofToken,
        deviceProofTokenSigned
      });

      if (pending) {
        setPendingAuth(pending);
        Alert.alert(
          'Pending Authentication',
          `You have a pending authentication request.\nChallenge required: ${pending.authAttemptChallengeRequired ? 'Yes' : 'No'}`
        );
      } else {
        Alert.alert('No Pending Requests', 'There are no pending authentication requests.');
        setPendingAuth(null);
      }
    } catch (error) {
      Alert.alert('Error', error.message || 'Failed to check pending auth');
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Respond to auth attempt
  const handleRespondToAuth = async (accepted) => {
    if (!pendingAuth) {
      return;
    }

    if (accepted && pendingAuth.authAttemptChallengeRequired && !authChallengeResponse) {
      Alert.alert('Error', 'Please enter the challenge code');
      return;
    }

    setLoading(true);
    try {
      // Sign the auth attempt proof token
      const authAttemptProofTokenSigned = ExpoCryptoNative.generateSignature(
        pendingAuth.authAttemptProofToken,
        enrollmentData.devicePrivateKey
      );

      const request = {
        authAttemptId: pendingAuth.authAttemptId,
        authAttemptAccepted: accepted,
        authAttemptProofTokenSignedByDevice: authAttemptProofTokenSigned
      };

      if (accepted && pendingAuth.authAttemptChallengeRequired) {
        request.authAttemptChallengeResponse = parseInt(authChallengeResponse);
      }

      const response = await AuthApiService.respondToAuth(request);

      Alert.alert(
        accepted ? 'Authorized' : 'Denied',
        response.message || `Authentication ${accepted ? 'approved' : 'denied'} successfully`,
        [{ text: 'OK', onPress: () => {
          setPendingAuth(null);
          setAuthChallengeResponse('');
        }}]
      );
    } catch (error) {
      Alert.alert('Error', error.message || 'Failed to respond to auth attempt');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="auto" />
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Ezkey Mobile V2</Text>
          <Text style={styles.headerSubtitle}>Step {step} of 3</Text>
        </View>

        {/* Step 1: Enrollment */}
        {step === 1 && (
          <View style={styles.stepContainer}>
            <Text style={styles.stepTitle}>Step 1: Enrollment</Text>
            <Text style={styles.stepDescription}>
              Enter your enrollment details to bind your device
            </Text>

            {!enrollmentData ? (
              <>
                <TextInput
                  style={styles.input}
                  placeholder="Enrollment ID"
                  value={enrollmentId}
                  onChangeText={setEnrollmentId}
                  keyboardType="numeric"
                  editable={!loading}
                />
                <TextInput
                  style={styles.input}
                  placeholder="Enrollment Proof Token"
                  value={enrollmentProofToken}
                  onChangeText={setEnrollmentProofToken}
                  editable={!loading}
                />
                
                {/* Version indicator for debugging */}
                <Text style={styles.debugText}>
                  🔧 Debug Build - Oct 18, 2025 (Crypto Module Fixed)
                </Text>
                
                <TouchableOpacity
                  style={[styles.button, loading && styles.buttonDisabled]}
                  onPress={handleEnrollmentBind}
                  disabled={loading}
                >
                  {loading ? (
                    <ActivityIndicator color="#fff" />
                  ) : (
                    <Text style={styles.buttonText}>Bind Device</Text>
                  )}
                </TouchableOpacity>
              </>
            ) : !enrollmentComplete ? (
              <>
                <View style={styles.infoBox}>
                  {enrollmentData.integrationLogo && (
                    <Image
                      source={{ uri: enrollmentData.integrationLogo }}
                      style={styles.logo}
                      resizeMode="contain"
                    />
                  )}
                  <Text style={styles.infoTitle}>{enrollmentData.integrationName}</Text>
                  <Text style={styles.infoText}>{enrollmentData.integrationDescription}</Text>
                  <Text style={styles.infoLabel}>Enrollment: {enrollmentData.enrollmentName}</Text>
                </View>

                <TextInput
                  style={styles.input}
                  placeholder="Challenge Code (6 digits)"
                  value={challengeCode}
                  onChangeText={setChallengeCode}
                  keyboardType="numeric"
                  maxLength={6}
                  editable={!loading}
                />
                <TouchableOpacity
                  style={[styles.button, loading && styles.buttonDisabled]}
                  onPress={handleEnrollmentVerify}
                  disabled={loading}
                >
                  {loading ? (
                    <ActivityIndicator color="#fff" />
                  ) : (
                    <Text style={styles.buttonText}>Verify Enrollment</Text>
                  )}
                </TouchableOpacity>
              </>
            ) : (
              <View style={styles.successBox}>
                <Text style={styles.successText}>✓ Enrollment Complete</Text>
                <TouchableOpacity
                  style={styles.button}
                  onPress={() => setStep(2)}
                >
                  <Text style={styles.buttonText}>Continue</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        )}

        {/* Step 2: Summary */}
        {step === 2 && (
          <View style={styles.stepContainer}>
            <Text style={styles.stepTitle}>Step 2: Ready to Authenticate</Text>
            <Text style={styles.stepDescription}>
              Your device is enrolled and ready to process authentication requests
            </Text>

            <View style={styles.summaryBox}>
              {enrollmentData?.integrationLogo && (
                <Image
                  source={{ uri: enrollmentData.integrationLogo }}
                  style={styles.logo}
                  resizeMode="contain"
                />
              )}
              {summary.map((item, index) => (
                <Text key={index} style={styles.summaryItem}>{item}</Text>
              ))}
            </View>

            <TouchableOpacity
              style={styles.button}
              onPress={handleProceedToAuth}
            >
              <Text style={styles.buttonText}>Check for Auth Requests</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Step 3: Auth Attempts */}
        {step === 3 && (
          <View style={styles.stepContainer}>
            <Text style={styles.stepTitle}>Step 3: Authentication Requests</Text>
            <Text style={styles.stepDescription}>
              Check for pending authentication requests
            </Text>

            <TouchableOpacity
              style={[styles.button, styles.buttonSecondary, loading && styles.buttonDisabled]}
              onPress={handleCheckPendingAuth}
              disabled={loading}
            >
              {loading ? (
                <ActivityIndicator color="#007AFF" />
              ) : (
                <Text style={styles.buttonTextSecondary}>Check Pending Requests</Text>
              )}
            </TouchableOpacity>

            {pendingAuth && (
              <View style={styles.authBox}>
                {enrollmentData?.integrationLogo && (
                  <Image
                    source={{ uri: enrollmentData.integrationLogo }}
                    style={styles.logo}
                    resizeMode="contain"
                  />
                )}
                <Text style={styles.authTitle}>Authentication Request</Text>
                <Text style={styles.authText}>
                  From: {enrollmentData?.integrationName}
                </Text>
                <Text style={styles.authText}>
                  Request ID: {pendingAuth.authAttemptId}
                </Text>
                
                {pendingAuth.authAttemptChallengeRequired && (
                  <TextInput
                    style={styles.input}
                    placeholder="Challenge Code (6 digits)"
                    value={authChallengeResponse}
                    onChangeText={setAuthChallengeResponse}
                    keyboardType="numeric"
                    maxLength={6}
                    editable={!loading}
                  />
                )}

                <View style={styles.buttonRow}>
                  <TouchableOpacity
                    style={[styles.button, styles.buttonDeny, loading && styles.buttonDisabled]}
                    onPress={() => handleRespondToAuth(false)}
                    disabled={loading}
                  >
                    <Text style={styles.buttonText}>Deny</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.button, styles.buttonApprove, loading && styles.buttonDisabled]}
                    onPress={() => handleRespondToAuth(true)}
                    disabled={loading}
                  >
                    <Text style={styles.buttonText}>Authorize</Text>
                  </TouchableOpacity>
                </View>
              </View>
            )}

            <TouchableOpacity
              style={[styles.button, styles.buttonBack]}
              onPress={() => setStep(2)}
            >
              <Text style={styles.buttonText}>Back to Summary</Text>
            </TouchableOpacity>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
    paddingVertical: 20,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#007AFF',
    marginBottom: 5,
  },
  headerSubtitle: {
    fontSize: 16,
    color: '#666',
  },
  stepContainer: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  stepTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 10,
  },
  stepDescription: {
    fontSize: 14,
    color: '#666',
    marginBottom: 20,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    marginBottom: 15,
    fontSize: 16,
    backgroundColor: '#fff',
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonSecondary: {
    backgroundColor: '#fff',
    borderWidth: 2,
    borderColor: '#007AFF',
  },
  buttonTextSecondary: {
    color: '#007AFF',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 10,
  },
  buttonDeny: {
    flex: 1,
    backgroundColor: '#FF3B30',
  },
  buttonApprove: {
    flex: 1,
    backgroundColor: '#34C759',
  },
  buttonBack: {
    backgroundColor: '#666',
    marginTop: 20,
  },
  infoBox: {
    backgroundColor: '#f9f9f9',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    alignItems: 'center',
  },
  logo: {
    width: 80,
    height: 80,
    marginBottom: 10,
  },
  infoTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 5,
    textAlign: 'center',
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 10,
    textAlign: 'center',
  },
  infoLabel: {
    fontSize: 14,
    color: '#007AFF',
    fontWeight: '600',
  },
  successBox: {
    backgroundColor: '#E8F5E9',
    padding: 20,
    borderRadius: 8,
    alignItems: 'center',
  },
  successText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2E7D32',
    marginBottom: 15,
  },
  summaryBox: {
    backgroundColor: '#f9f9f9',
    padding: 20,
    borderRadius: 8,
    marginBottom: 20,
    alignItems: 'center',
  },
  summaryItem: {
    fontSize: 16,
    color: '#333',
    marginVertical: 5,
  },
  authBox: {
    backgroundColor: '#FFF3E0',
    padding: 20,
    borderRadius: 8,
    marginVertical: 20,
    alignItems: 'center',
  },
  authTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#E65100',
    marginBottom: 10,
  },
  authText: {
    fontSize: 14,
    color: '#666',
    marginVertical: 3,
  },
  debugText: {
    fontSize: 12,
    color: '#FF9800',
    textAlign: 'center',
    marginVertical: 10,
    fontWeight: '600',
  },
});
