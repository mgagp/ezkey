"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Demo: Ezkey JavaScript/TypeScript SDK Demo
 * Description: Demo application showcasing complete Ezkey SDK integration workflow
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const src_1 = require("../src");
const crypto = __importStar(require("crypto"));
const readline = __importStar(require("readline"));
/**
 * Demo application showcasing complete Ezkey SDK integration workflow.
 *
 * This demo demonstrates:
 * 1. Creating an integration
 * 2. Creating an enrollment
 * 3. Binding and verifying the enrollment
 * 4. Creating an authorization attempt
 * 5. Checking and fetching pending authorization attempts
 * 6. Accepting/denying authorization attempts
 */
class EzkeyDemo {
    constructor() {
        this.client = src_1.EzkeyClient.create();
        this.rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
    }
    async run() {
        console.log('=== Ezkey JavaScript/TypeScript SDK Demo ===\n');
        try {
            // Initialize SDK client
            console.log('✓ Initialized Ezkey client with default configuration');
            console.log(`  Admin API: ${this.client.getConfig().adminApiUrl}`);
            console.log(`  Auth API: ${this.client.getConfig().authApiUrl}\n`);
            // Step 1: Create Integration
            console.log('Step 1: Creating integration...');
            const integration = await this.client.admin().createIntegration('https://acme.com/logo.png', 'ACME Demo App', 'Demo application for Ezkey integration testing');
            console.log(`✓ Created integration with ID: ${integration.id}\n`);
            // Step 2: Create Enrollment
            console.log('Step 2: Creating enrollment...');
            const enrollment = await this.client.admin().createEnrollment(integration.id, 'Demo Device', true // Challenge required
            );
            console.log(`✓ Created enrollment with ID: ${enrollment.enrollmentId}`);
            console.log(`✓ Enrollment challenge: ${enrollment.enrollmentChallenge}\n`);
            // Step 3: Bind and Verify Enrollment (Device Side)
            console.log('Step 3: Binding and verifying enrollment...');
            // Generate device key pair
            const deviceKeys = this.generateKeyPair();
            const devicePublicKey = this.encodePublicKey(deviceKeys.publicKey);
            console.log('✓ Generated device key pair');
            // Bind enrollment
            const bindResponse = await this.client.auth().bindEnrollment(enrollment.enrollmentId);
            console.log('✓ Bound enrollment');
            console.log(`  Integration: ${bindResponse.integrationName}`);
            console.log(`  Description: ${bindResponse.integrationDescription}`);
            // Sign enrollment proof token
            const enrollmentProofTokenSigned = this.signData(bindResponse.enrollmentProofToken, deviceKeys.privateKey);
            // Verify enrollment
            const verifyResponse = await this.client.auth().verifyEnrollment(enrollment.enrollmentId, enrollment.enrollmentChallenge, devicePublicKey, enrollmentProofTokenSigned);
            console.log(`✓ Verified enrollment, active: ${verifyResponse.active}\n`);
            // Step 4: Create Authorization Attempt
            console.log('Step 4: Creating authorization attempt...');
            const authAttempt = await this.client.admin().createAuthAttempt(enrollment.enrollmentId, true // Challenge requested
            );
            console.log(`✓ Created auth attempt with ID: ${authAttempt.authAttemptId}\n`);
            // Step 5: Check for Pending Authorization Attempts (Device Side)
            console.log('Step 5: Checking for pending authorization attempts...');
            // Generate device proof token for this session
            const deviceProofToken = `device-proof-${Date.now()}`;
            const deviceProofTokenSigned = this.signData(deviceProofToken, deviceKeys.privateKey);
            const pendingAuth = await this.client.auth().checkPendingAuth(enrollment.enrollmentId, deviceProofToken, deviceProofTokenSigned);
            if (pendingAuth) {
                console.log(`✓ Found pending auth attempt with ID: ${pendingAuth.authAttemptId}`);
                console.log(`  Challenge required: ${pendingAuth.authAttemptChallengeRequired}\n`);
                // Step 6: Accept/Deny Authorization Attempt
                console.log('Step 6: Responding to authorization attempt...');
                const accepted = await this.askQuestion('Accept this authentication request? (y/n): ');
                const shouldAccept = accepted.toLowerCase() === 'y' || accepted.toLowerCase() === 'yes';
                // Sign the auth attempt proof token
                const authProofTokenSigned = this.signData(pendingAuth.authAttemptProofToken, deviceKeys.privateKey);
                // Respond to auth attempt
                const respondResponse = await this.client.auth().respondToAuth(pendingAuth.authAttemptId, authProofTokenSigned, shouldAccept, shouldAccept ? 123456 : undefined // Challenge response if accepting
                );
                console.log(`✓ Auth response sent: ${respondResponse.result}`);
                console.log(`  Message: ${respondResponse.message}\n`);
                // Wait for completion (Admin Side)
                console.log('Waiting for authentication completion...');
                const waitResponse = await this.client.admin().waitForResponse(authAttempt.authAttemptId);
                console.log('✓ Authentication completed');
                console.log(`  Status: ${waitResponse.status}`);
                console.log(`  Completed: ${waitResponse.completed}`);
                console.log(`  Wait duration: ${waitResponse.waitDuration}s`);
            }
            else {
                console.log('✗ No pending authentication requests found');
            }
            console.log('\n=== Demo completed successfully! ===');
        }
        catch (error) {
            if (error instanceof src_1.EzkeyException) {
                console.error(`Demo failed: ${error.message}`);
                if (error.statusCode) {
                    console.error(`Status code: ${error.statusCode}`);
                }
                if (error.responseBody) {
                    console.error(`Response: ${error.responseBody}`);
                }
            }
            else {
                console.error(`Demo failed: ${error}`);
            }
            process.exit(1);
        }
        finally {
            this.rl.close();
        }
    }
    /**
     * Generates an RSA key pair for device simulation.
     */
    generateKeyPair() {
        return crypto.generateKeyPairSync('rsa', {
            modulusLength: 2048,
            publicKeyEncoding: {
                type: 'spki',
                format: 'pem'
            },
            privateKeyEncoding: {
                type: 'pkcs8',
                format: 'pem'
            }
        });
    }
    /**
     * Encodes a public key to Base64 string.
     */
    encodePublicKey(publicKey) {
        // Remove PEM headers and whitespace, then base64 encode
        const keyData = publicKey
            .replace(/-----BEGIN PUBLIC KEY-----/g, '')
            .replace(/-----END PUBLIC KEY-----/g, '')
            .replace(/\s/g, '');
        return keyData;
    }
    /**
     * Signs data with a private key using SHA256withRSA.
     */
    signData(data, privateKey) {
        const sign = crypto.createSign('SHA256');
        sign.update(data, 'utf8');
        const signature = sign.sign(privateKey, 'base64');
        return signature;
    }
    /**
     * Prompts user for input.
     */
    askQuestion(question) {
        return new Promise((resolve) => {
            this.rl.question(question, (answer) => {
                resolve(answer.trim());
            });
        });
    }
}
// Run the demo
const demo = new EzkeyDemo();
demo.run().catch((error) => {
    console.error('Unhandled error:', error);
    process.exit(1);
});
//# sourceMappingURL=demo.js.map