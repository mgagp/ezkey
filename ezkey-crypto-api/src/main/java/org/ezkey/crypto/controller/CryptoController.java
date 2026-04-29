/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: CryptoController
 * Description: REST controller for crypto API v1 providing crypto services for testing.
 */

package org.ezkey.crypto.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.service.AuthAttemptSignaturePayload;
import org.ezkey.crypto.dto.DecryptRequestDto;
import org.ezkey.crypto.dto.DecryptResponseDto;
import org.ezkey.crypto.dto.ECP256KeyPairResponseDto;
import org.ezkey.crypto.dto.Ed25519KeyPairResponseDto;
import org.ezkey.crypto.dto.EncryptRequestDto;
import org.ezkey.crypto.dto.EncryptResponseDto;
import org.ezkey.crypto.dto.HashTokenRequestDto;
import org.ezkey.crypto.dto.HashTokenResponseDto;
import org.ezkey.crypto.dto.PayloadHelperRequestDto;
import org.ezkey.crypto.dto.PayloadHelperResponseDto;
import org.ezkey.crypto.dto.ProofTokenResponseDto;
import org.ezkey.crypto.dto.SignDataRequestDto;
import org.ezkey.crypto.dto.SignDataResponseDto;
import org.ezkey.crypto.dto.ValidateSignatureRequestDto;
import org.ezkey.crypto.dto.ValidateSignatureResponseDto;
import org.ezkey.enrollment.service.EnrollmentSignaturePayload;
import org.ezkey.enrollment.service.EnrollmentSignaturePayload.EnrollmentVerificationOutcome;
import org.ezkey.security.EncryptionService;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Crypto API.
 *
 * <p>This controller exposes endpoints for various cryptographic operations, including:
 *
 * <ul>
 *   <li>Generating cryptographically secure proof tokens.
 *   <li>Creating EC P-256 key pairs for device simulation.
 *   <li>Signing data with a private key.
 *   <li>Validating signatures with a public key.
 *   <li>Encrypting plaintext values for testing and debugging.
 *   <li>Decrypting encrypted database column values for debugging.
 * </ul>
 *
 * <p>It is designed primarily for testing, development, and integration scenarios where direct
 * access to cryptographic functions is required.
 *
 * @since 2025
 */
@Tag(name = "Crypto API", description = "Crypto services for testing and integration")
@RestController
@RequestMapping("/api/v1/crypto")
public class CryptoController {

  private static final Logger logger = LoggerFactory.getLogger(CryptoController.class);

  private final SignatureService signatureService;
  private final EncryptionService encryptionService;

  /**
   * Create a new {@link CryptoController}.
   *
   * @param signatureService signature service used for EC P-256 operations
   * @param encryptionService encryption service used for decrypting encrypted database values
   */
  public CryptoController(SignatureService signatureService, EncryptionService encryptionService) {
    this.signatureService = signatureService;
    this.encryptionService = encryptionService;
  }

  @Operation(
      summary = "Generate proof token",
      description =
          "Generates a cryptographically secure proof token for use in authentication flows")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Proof token generated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @GetMapping("/prooftoken")
  public ResponseEntity<ProofTokenResponseDto> prooftoken() {
    var response = new ProofTokenResponseDto(signatureService.generateProofToken());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Generate EC P-256 key pair",
      description =
          "Generates a new EC P-256 (secp256r1) key pair for use in device simulation. "
              + "Private key is in PKCS#8 format and public key is in X.509 format.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "EC P-256 key pair generated successfully"),
        @ApiResponse(responseCode = "500", description = "Key generation failed")
      })
  @GetMapping("/keypair")
  public ResponseEntity<ECP256KeyPairResponseDto> generateKeyPair() {
    ECP256KeyPair keyPair = signatureService.generateECP256KeyPair();
    var response =
        new ECP256KeyPairResponseDto(keyPair.base64PrivateKey(), keyPair.base64PublicKey());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Generate Ed25519 integration key pair",
      description =
          "Generates a new Ed25519 key pair for integration signing. Private key is PKCS#8 Base64;"
              + " public key is raw 32 bytes as Base64URL (no padding).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Ed25519 key pair generated successfully"),
        @ApiResponse(responseCode = "500", description = "Key generation failed")
      })
  @GetMapping("/integration-keypair")
  public ResponseEntity<Ed25519KeyPairResponseDto> generateIntegrationKeyPair() {
    Ed25519KeyPair keyPair = signatureService.generateEd25519KeyPair();
    var response =
        new Ed25519KeyPairResponseDto(keyPair.base64PrivateKey(), keyPair.base64UrlPublicKey());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Sign data with private key",
      description =
          "Signs the provided data using EC P-256 ECDSA-SHA256 signature "
              + "with the given private key")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Data signed successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data or malformed private key"),
        @ApiResponse(responseCode = "500", description = "Signing operation failed")
      })
  @PostMapping("/sign")
  public ResponseEntity<SignDataResponseDto> signData(
      @Valid @RequestBody SignDataRequestDto request) {
    String signature = signatureService.signEcdsaSha256(request.getData(), request.getPrivateKey());
    var response = new SignDataResponseDto(signature, request.getData(), "EC_P256");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Sign data with Ed25519 integration key",
      description =
          "Signs the provided UTF-8 data using an Ed25519 private key in PKCS#8 Base64 format. "
              + "Returns the raw 64-byte signature encoded as Base64URL without padding.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Data signed successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data or malformed private key"),
        @ApiResponse(responseCode = "500", description = "Signing operation failed")
      })
  @PostMapping("/sign-ed25519")
  public ResponseEntity<SignDataResponseDto> signEd25519(
      @Valid @RequestBody SignDataRequestDto request) {
    String signature =
        signatureService.signIntegrationPayload(request.getData(), request.getPrivateKey());
    var response = new SignDataResponseDto(signature, request.getData(), "ED25519");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Validate signature",
      description =
          "Validates an EC P-256 ECDSA-SHA256 signature "
              + "against the original data using the provided public key")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Signature validation completed"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data, malformed signature or public key"),
        @ApiResponse(responseCode = "500", description = "Validation operation failed")
      })
  @PostMapping("/validate")
  public ResponseEntity<ValidateSignatureResponseDto> validateSignature(
      @Valid @RequestBody ValidateSignatureRequestDto request) {
    boolean isValid =
        signatureService.validateSignature(
            request.getData(), request.getSignature(), request.getPublicKey());

    String message = isValid ? "Signature is valid" : "Signature is invalid";
    var response = new ValidateSignatureResponseDto(isValid, message, "EC_P256");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Verify Ed25519 integration signature",
      description =
          "Verifies an Ed25519 signature over the provided UTF-8 data using a raw 32-byte "
              + "public key encoded as Base64URL without padding. Standard Base64 is also "
              + "accepted for compatibility.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Signature verification completed"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data, malformed signature or public key"),
        @ApiResponse(responseCode = "500", description = "Verification operation failed")
      })
  @PostMapping("/verify-ed25519")
  public ResponseEntity<ValidateSignatureResponseDto> verifyEd25519(
      @Valid @RequestBody ValidateSignatureRequestDto request) {
    boolean isValid =
        signatureService.verifyIntegrationSignature(
            request.getData(), request.getSignature(), request.getPublicKey());

    String message = isValid ? "Signature is valid" : "Signature is invalid";
    var response = new ValidateSignatureResponseDto(isValid, message, "ED25519");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Build canonical EZKey payload",
      description =
          "Builds the canonical EZKey payload for auth-attempt (pending, respond, respond-result)"
              + " or enrollment (enrollment-bind, enrollment-verify-device,"
              + " enrollment-verify-result) flows. Applies NFC normalization to the text fields"
              + " defined by the protocol so the result can be used as a validation oracle in Dart"
              + " tests and Postman workflows.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Payload built successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or payload type")
      })
  @PostMapping("/payload-helper")
  public ResponseEntity<PayloadHelperResponseDto> buildPayload(
      @Valid @RequestBody PayloadHelperRequestDto request) {
    String payloadType = request.getType().trim().toLowerCase(Locale.ROOT);
    String payload =
        switch (payloadType) {
          case "pending" -> buildPendingPayload(request);
          case "respond" -> buildRespondPayload(request);
          case "respond-result" -> buildRespondResultPayload(request);
          case "enrollment-bind" -> buildEnrollmentBindPayload(request);
          case "enrollment-verify-device" -> buildEnrollmentVerifyDevicePayload(request);
          case "enrollment-verify-result" -> buildEnrollmentVerifyResultPayload(request);
          default ->
              throw new IllegalArgumentException("Unsupported payload type: " + request.getType());
        };

    var response =
        new PayloadHelperResponseDto(payloadType, payload, "UTF-8 + NFC where applicable");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Encrypt plaintext value",
      description =
          "Encrypts a plaintext value and returns it in the standard encrypted format "
              + "(ENC:keyID:Base64(ciphertext)). This endpoint is designed for testing and "
              + "debugging purposes, enabling generation of encrypted test data and verification "
              + "of encryption/decryption round-trips.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Encryption operation completed"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data (empty plaintext value)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping("/encrypt")
  public ResponseEntity<EncryptResponseDto> encrypt(@Valid @RequestBody EncryptRequestDto request) {
    String plaintext = request.getPlaintext();

    String keyId = null;
    String encryptedFormat = "PLAINTEXT";
    String encryptedValue = null;
    boolean encryptionSuccessful = false;
    String errorMessage = null;
    boolean encryptionAvailable = false;

    try {
      encryptionAvailable = encryptionService.isEncryptionAvailable();

      if (!encryptionAvailable) {
        // Encryption service not available, return plaintext
        encryptedValue = plaintext;
        encryptedFormat = "PLAINTEXT";
        encryptionSuccessful = false;
        errorMessage = "Encryption service not available - returning plaintext.";
      } else {
        // Encryption available: attempt encryption
        try {
          String encrypted = encryptionService.encrypt(plaintext);

          // Check if encryption was successful (result is different from input and has ENC: prefix)
          if (encrypted != null && !encrypted.equals(plaintext) && encrypted.startsWith("ENC:")) {
            encryptedValue = encrypted;
            encryptedFormat = "ENC:keyID:Base64";
            encryptionSuccessful = true;

            // Extract key ID from encrypted value
            Long keyIdLong = encryptionService.parseKeyIdFromPrefix(encrypted);
            if (keyIdLong != null) {
              keyId = Long.toUnsignedString(keyIdLong);
            }
          } else {
            // Encryption returned plaintext (e.g., already encrypted or failed)
            encryptedValue = encrypted;
            encryptedFormat = "PLAINTEXT";
            encryptionSuccessful = false;

            // If input was already encrypted, explain that
            if (plaintext.startsWith("ENC:") && encryptionService.isEncrypted(plaintext)) {
              errorMessage = "Value is already encrypted - skipped re-encryption.";
            } else {
              errorMessage = "Encryption failed: value returned unchanged.";
            }
          }
        } catch (IllegalStateException e) {
          // Encryption failed with exception
          logger.error("Encryption failed with exception", e);
          encryptedValue = plaintext;
          encryptedFormat = "PLAINTEXT";
          encryptionSuccessful = false;
          errorMessage = "Encryption failed: " + e.getMessage();
        }
      }
    } catch (Exception e) {
      // Log the full exception for debugging
      logger.error("Exception during encryption operation", e);
      // Catch any unexpected exceptions and return error information
      String exceptionMessage = e.getMessage();
      if (exceptionMessage == null) {
        exceptionMessage = e.getClass().getSimpleName();
      }
      errorMessage = "Unexpected error during encryption: " + exceptionMessage;
      encryptionSuccessful = false;
      encryptedValue = plaintext;
      encryptedFormat = "PLAINTEXT";
    }

    var response =
        new EncryptResponseDto(
            encryptedValue,
            encryptionSuccessful,
            keyId,
            encryptedFormat,
            errorMessage,
            encryptionAvailable);
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Hash bearer token (SHA-256 hex)",
      description =
          "Computes the SHA-256 hash (hexadecimal) of a bearer token. Used for development and "
              + "debugging when admin tokens are stored as hashes in the database "
              + "(ezkey_admin_tokens.bearer_token_hash). Send a token in the request body to get "
              + "the hash that would be used for lookup.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Token hash computed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request (empty or blank token)"),
      })
  @PostMapping("/hash-token")
  public ResponseEntity<HashTokenResponseDto> hashToken(
      @Valid @RequestBody HashTokenRequestDto request) {
    String token = request.getToken();
    String tokenHash = SensitiveDataHasher.sha256Hex(token);
    var response = new HashTokenResponseDto(tokenHash != null ? tokenHash : "");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Decrypt encrypted database column value",
      description =
          "Decrypts an encrypted database column value for debugging purposes. "
              + "Accepts values in ENC:keyID:Base64(ciphertext) format and returns "
              + "comprehensive debugging metadata including decrypted plaintext, validation "
              + "information, and error details if decryption fails.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Decryption operation completed"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data (empty encrypted value)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping("/decrypt")
  public ResponseEntity<DecryptResponseDto> decrypt(@Valid @RequestBody DecryptRequestDto request) {
    String encryptedValue = request.getEncryptedValue();

    String keyId = null;
    String encryptedFormat = "PLAINTEXT";
    String plaintext = null;
    boolean decryptionSuccessful = false;
    String errorMessage = null;
    boolean encryptionAvailable = false;
    boolean isEncrypted = false;

    try {
      encryptionAvailable = encryptionService.isEncryptionAvailable();

      // "isEncrypted" is defined as "has ENC: prefix" for debugging usability.
      boolean hasEncPrefix = encryptedValue != null && encryptedValue.startsWith("ENC:");
      isEncrypted = hasEncPrefix;

      // If value does not look encrypted, return it as plaintext (even if encryption
      // is disabled).
      if (!hasEncPrefix) {
        plaintext = encryptedValue;
        decryptionSuccessful = true;
      } else {
        // Check if format is valid
        boolean isValidFormat = encryptionService.isEncrypted(encryptedValue);

        if (!isValidFormat) {
          // ENC: prefix exists but format is invalid (strict pattern mismatch).
          encryptedFormat = "ENC:INVALID";
          decryptionSuccessful = false;
          plaintext = null;
          errorMessage = "Invalid encrypted format. Expected ENC:keyID:Base64(ciphertext).";
        } else if (!encryptionAvailable) {
          // Valid encrypted format, but encryption is not initialized.
          encryptedFormat = "ENC:keyID:Base64";
          Long keyIdLong = encryptionService.parseKeyIdFromPrefix(encryptedValue);
          if (keyIdLong != null) {
            keyId = Long.toUnsignedString(keyIdLong);
          }
          decryptionSuccessful = false;
          plaintext = null;
          errorMessage = "Encryption service not available - cannot decrypt encrypted value.";
        } else {
          // Valid encrypted format and encryption available: attempt decryption.
          encryptedFormat = "ENC:keyID:Base64";
          Long keyIdLong = encryptionService.parseKeyIdFromPrefix(encryptedValue);
          if (keyIdLong != null) {
            keyId = Long.toUnsignedString(keyIdLong);
          }

          // Attempt decryption - decrypt() returns original value on failure.
          String decryptedResult = encryptionService.decrypt(encryptedValue);
          if (decryptedResult != null && !decryptedResult.equals(encryptedValue)) {
            plaintext = decryptedResult;
            decryptionSuccessful = true;
          } else {
            plaintext = null;
            decryptionSuccessful = false;
            errorMessage =
                "Decryption failed: value returned unchanged (invalid key, corrupted data, or key"
                    + " not available).";
          }
        }
      }
    } catch (Exception e) {
      // Log the full exception for debugging
      logger.error("Exception during decryption operation", e);
      // Catch any unexpected exceptions and return error information
      String exceptionMessage = e.getMessage();
      if (exceptionMessage == null) {
        exceptionMessage = e.getClass().getSimpleName();
      }
      errorMessage = "Unexpected error during decryption: " + exceptionMessage;
      decryptionSuccessful = false;
      plaintext = null;
      // Try to extract keyId even if decryption failed
      if (encryptedValue != null && encryptedValue.startsWith("ENC:")) {
        try {
          Long keyIdLong = encryptionService.parseKeyIdFromPrefix(encryptedValue);
          if (keyIdLong != null) {
            keyId = Long.toUnsignedString(keyIdLong);
            encryptedFormat = "ENC:keyID:Base64";
          }
        } catch (Exception ignored) {
          // Ignore errors when trying to extract keyId
        }
      }
    }

    var response =
        new DecryptResponseDto(
            plaintext,
            isEncrypted,
            decryptionSuccessful,
            keyId,
            encryptedFormat,
            errorMessage,
            encryptionAvailable);
    return ResponseEntity.ok(response);
  }

  private static String buildPendingPayload(PayloadHelperRequestDto request) {
    if (request.getChallengeRequired() == null) {
      throw new IllegalArgumentException("challengeRequired is required for pending payloads");
    }
    if (request.getChallengeRequiredByPolicy() == null) {
      throw new IllegalArgumentException(
          "challengeRequiredByPolicy is required for pending payloads");
    }
    return AuthAttemptSignaturePayload.buildPendingPayload(
        request.getProofToken(),
        request.getChallengeRequired(),
        request.getChallengeRequiredByPolicy(),
        request.getContextTitle(),
        request.getContextMessage());
  }

  private static String buildRespondPayload(PayloadHelperRequestDto request) {
    if (request.getAccepted() == null) {
      throw new IllegalArgumentException("accepted is required for respond payloads");
    }
    return AuthAttemptSignaturePayload.buildRespondPayload(
        request.getProofToken(), request.getAccepted());
  }

  private static String buildRespondResultPayload(PayloadHelperRequestDto request) {
    if (request.getAuthAttemptId() == null) {
      throw new IllegalArgumentException("authAttemptId is required for respond-result payloads");
    }
    if (request.getResult() == null || request.getResult().isBlank()) {
      throw new IllegalArgumentException("result is required for respond-result payloads");
    }
    AuthenticationResult authenticationResult;
    try {
      authenticationResult =
          AuthenticationResult.valueOf(request.getResult().trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unsupported authentication result: " + request.getResult());
    }

    return AuthAttemptSignaturePayload.buildRespondResultPayload(
        request.getProofToken(),
        request.getAuthAttemptId(),
        authenticationResult,
        request.getMessage());
  }

  private static String buildEnrollmentBindPayload(PayloadHelperRequestDto request) {
    if (request.getEnrollmentId() == null) {
      throw new IllegalArgumentException("enrollmentId is required for enrollment-bind payloads");
    }
    if (request.getIntegrationPublicKey() == null || request.getIntegrationPublicKey().isBlank()) {
      throw new IllegalArgumentException(
          "integrationPublicKey is required for enrollment-bind payloads");
    }
    if (request.getIntegrationKeyAlgorithm() == null
        || request.getIntegrationKeyAlgorithm().isBlank()) {
      throw new IllegalArgumentException(
          "integrationKeyAlgorithm is required for enrollment-bind payloads");
    }
    if (request.getChallengeRequiredByPolicy() == null) {
      throw new IllegalArgumentException(
        "challengeRequiredByPolicy is required for enrollment-bind payloads");
    }
    return EnrollmentSignaturePayload.buildBindPayload(
        request.getProofToken(),
        request.getEnrollmentId(),
        request.getIntegrationPublicKey(),
        request.getIntegrationKeyAlgorithm(),
        request.getIntegrationName(),
        request.getIntegrationDescription(),
        request.getEnrollmentName(),
        request.getTenantId(),
        request.getTenantName(),
          request.getTenantDescription(),
          request.getChallengeRequiredByPolicy());
  }

  private static String buildEnrollmentVerifyDevicePayload(PayloadHelperRequestDto request) {
    if (request.getEnrollmentId() == null) {
      throw new IllegalArgumentException(
          "enrollmentId is required for enrollment-verify-device payloads");
    }
    if (request.getChallengeResponse() == null) {
      throw new IllegalArgumentException(
          "challengeResponse is required for enrollment-verify-device payloads");
    }
    if (request.getDevicePublicKey() == null || request.getDevicePublicKey().isBlank()) {
      throw new IllegalArgumentException(
          "devicePublicKey is required for enrollment-verify-device payloads");
    }
    return EnrollmentSignaturePayload.buildVerifyDevicePayload(
        request.getProofToken(),
        request.getEnrollmentId(),
        request.getChallengeResponse(),
        request.getDevicePublicKey());
  }

  private static String buildEnrollmentVerifyResultPayload(PayloadHelperRequestDto request) {
    if (request.getEnrollmentId() == null) {
      throw new IllegalArgumentException(
          "enrollmentId is required for enrollment-verify-result payloads");
    }
    if (request.getResult() == null || request.getResult().isBlank()) {
      throw new IllegalArgumentException(
          "result is required for enrollment-verify-result payloads");
    }
    EnrollmentVerificationOutcome outcome;
    try {
      outcome =
          EnrollmentVerificationOutcome.valueOf(
              request.getResult().trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unsupported enrollment verification outcome: " + request.getResult());
    }
    return EnrollmentSignaturePayload.buildVerifyResultPayload(
        request.getProofToken(), request.getEnrollmentId(), outcome, request.getMessage());
  }
}
