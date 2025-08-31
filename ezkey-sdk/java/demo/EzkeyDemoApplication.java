/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Demo: EzkeyDemoApplication
 * Description: Demo application showcasing complete Ezkey SDK integration workflow
 */

package org.ezkey.sdk.demo;

import org.ezkey.sdk.*;
import org.ezkey.sdk.admin.generated.model.*;
import org.ezkey.sdk.auth.generated.model.*;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

/**
 * Demo application showcasing complete Ezkey SDK integration workflow.
 * <p>
 * This demo demonstrates:
 * 1. Creating an integration
 * 2. Creating an enrollment
 * 3. Binding and verifying the enrollment
 * 4. Creating an authorization attempt
 * 5. Checking and fetching pending authorization attempts
 * 6. Accepting/denying authorization attempts
 * </p>
 * 
 * @since 2025
 */
public class EzkeyDemoApplication {
    private static final Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        System.out.println("=== Ezkey Java SDK Demo ===");
        System.out.println();
        
        try {
            // Initialize SDK client
            EzkeyClient client = EzkeyClient.create();
            System.out.println("✓ Initialized Ezkey client with default configuration");
            System.out.println("  Admin API: " + client.getConfig().getAdminApiUrl());
            System.out.println("  Auth API: " + client.getConfig().getAuthApiUrl());
            System.out.println();
            
            // Step 1: Create Integration
            System.out.println("Step 1: Creating integration...");
            IntegrationCreateResponseDto integration = client.admin().createIntegration(
                "https://acme.com/logo.png",
                "ACME Demo App",
                "Demo application for Ezkey integration testing"
            );
            System.out.println("✓ Created integration with ID: " + integration.getId());
            System.out.println();
            
            // Step 2: Create Enrollment
            System.out.println("Step 2: Creating enrollment...");
            EnrollmentCreateResponseDto enrollment = client.admin().createEnrollment(
                integration.getId(),
                "Demo Device",
                true // Challenge required
            );
            System.out.println("✓ Created enrollment with ID: " + enrollment.getEnrollmentId());
            System.out.println("✓ Enrollment challenge: " + enrollment.getEnrollmentChallenge());
            System.out.println();
            
            // Step 3: Bind and Verify Enrollment (Device Side)
            System.out.println("Step 3: Binding and verifying enrollment...");
            
            // Generate device key pair
            KeyPair deviceKeys = generateKeyPair();
            String devicePublicKey = encodePublicKey(deviceKeys.getPublic());
            System.out.println("✓ Generated device key pair");
            
            // Bind enrollment
            EnrollmentBindResponseDto bindResponse = client.auth().bindEnrollment(enrollment.getEnrollmentId());
            System.out.println("✓ Bound enrollment");
            System.out.println("  Integration: " + bindResponse.getIntegrationName());
            System.out.println("  Description: " + bindResponse.getIntegrationDescription());
            
            // Sign enrollment proof token
            String enrollmentProofTokenSigned = signData(bindResponse.getEnrollmentProofToken(), deviceKeys.getPrivate());
            
            // Verify enrollment
            EnrollmentVerifyResponseDto verifyResponse = client.auth().verifyEnrollment(
                enrollment.getEnrollmentId(),
                enrollment.getEnrollmentChallenge(),
                devicePublicKey,
                enrollmentProofTokenSigned
            );
            System.out.println("✓ Verified enrollment, active: " + verifyResponse.getActive());
            System.out.println();
            
            // Step 4: Create Authorization Attempt
            System.out.println("Step 4: Creating authorization attempt...");
            AuthAttemptCreateResponseDto authAttempt = client.admin().createAuthAttempt(
                enrollment.getEnrollmentId(),
                true // Challenge requested
            );
            System.out.println("✓ Created auth attempt with ID: " + authAttempt.getAuthAttemptId());
            System.out.println();
            
            // Step 5: Check for Pending Authorization Attempts (Device Side)
            System.out.println("Step 5: Checking for pending authorization attempts...");
            
            // Generate device proof token for this session
            String deviceProofToken = "device-proof-" + System.currentTimeMillis();
            String deviceProofTokenSigned = signData(deviceProofToken, deviceKeys.getPrivate());
            
            AuthAttemptPendingResponseDto pendingAuth = client.auth().checkPendingAuth(
                enrollment.getEnrollmentId(),
                deviceProofToken,
                deviceProofTokenSigned
            );
            
            if (pendingAuth != null) {
                System.out.println("✓ Found pending auth attempt with ID: " + pendingAuth.getAuthAttemptId());
                System.out.println("  Challenge required: " + pendingAuth.getAuthAttemptChallengeRequired());
                System.out.println();
                
                // Step 6: Accept/Deny Authorization Attempt
                System.out.println("Step 6: Responding to authorization attempt...");
                System.out.print("Accept this authentication request? (y/n): ");
                String response = scanner.nextLine().trim().toLowerCase();
                boolean accepted = response.equals("y") || response.equals("yes");
                
                // Sign the auth attempt proof token
                String authProofTokenSigned = signData(pendingAuth.getAuthAttemptProofToken(), deviceKeys.getPrivate());
                
                // Respond to auth attempt
                AuthAttemptRespondResponseDto respondResponse = client.auth().respondToAuth(
                    pendingAuth.getAuthAttemptId(),
                    authProofTokenSigned,
                    accepted,
                    accepted ? 123456 : null // Challenge response if accepting
                );
                
                System.out.println("✓ Auth response sent: " + respondResponse.getResult());
                System.out.println("  Message: " + respondResponse.getMessage());
                System.out.println();
                
                // Wait for completion (Admin Side)
                System.out.println("Waiting for authentication completion...");
                AuthAttemptWaitResponseDto waitResponse = client.admin().waitForResponse(authAttempt.getAuthAttemptId());
                System.out.println("✓ Authentication completed");
                System.out.println("  Status: " + waitResponse.getStatus());
                System.out.println("  Completed: " + waitResponse.getCompleted());
                System.out.println("  Wait duration: " + waitResponse.getWaitDuration() + "s");
                
            } else {
                System.out.println("✗ No pending authentication requests found");
            }
            
            System.out.println();
            System.out.println("=== Demo completed successfully! ===");
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Generates an RSA key pair for device simulation.
     */
    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }
    
    /**
     * Encodes a public key to Base64 string.
     */
    private static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
    
    /**
     * Signs data with a private key using SHA256withRSA.
     */
    private static String signData(String data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes("UTF-8"));
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }
}