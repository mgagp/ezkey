/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: CryptoController
 * Description: REST controller for crypto API v1 providing crypto services for testing.
 */

package org.ezkey.crypto.controller;

import org.ezkey.crypto.dto.DecryptRequestDto;
import org.ezkey.crypto.dto.DecryptResponseDto;
import org.ezkey.crypto.dto.ECP256KeyPairResponseDto;
import org.ezkey.crypto.dto.ProofTokenResponseDto;
import org.ezkey.crypto.dto.SignDataRequestDto;
import org.ezkey.crypto.dto.SignDataResponseDto;
import org.ezkey.crypto.dto.ValidateSignatureRequestDto;
import org.ezkey.crypto.dto.ValidateSignatureResponseDto;
import org.ezkey.security.EncryptionService;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST controller for the Crypto API.
 *
 * <p>
 * This controller exposes endpoints for various cryptographic operations,
 * including:
 *
 * <ul>
 * <li>Generating cryptographically secure proof tokens.
 * <li>Creating EC P-256 key pairs for device simulation.
 * <li>Signing data with a private key.
 * <li>Validating signatures with a public key.
 * <li>Decrypting encrypted database column values for debugging.
 * </ul>
 *
 * <p>
 * It is designed primarily for testing, development, and integration scenarios
 * where direct
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
   * @param signatureService  signature service used for EC P-256 operations
   * @param encryptionService encryption service used for decrypting encrypted
   *                          database values
   */
  public CryptoController(SignatureService signatureService, EncryptionService encryptionService) {
    this.signatureService = signatureService;
    this.encryptionService = encryptionService;
  }

  @Operation(summary = "Generate proof token", description = "Generates a cryptographically secure proof token for use in authentication flows")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Proof token generated successfully"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @GetMapping("/prooftoken")
  public ResponseEntity<ProofTokenResponseDto> prooftoken() {
    var response = new ProofTokenResponseDto(signatureService.generateProofToken());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "Generate EC P-256 key pair", description = "Generates a new EC P-256 (secp256r1) key pair for use in device simulation. "
      + "Private key is in PKCS#8 format and public key is in X.509 format.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "EC P-256 key pair generated successfully"),
      @ApiResponse(responseCode = "500", description = "Key generation failed")
  })
  @GetMapping("/keypair")
  public ResponseEntity<ECP256KeyPairResponseDto> generateKeyPair() {
    ECP256KeyPair keyPair = signatureService.generateECP256KeyPair();
    var response = new ECP256KeyPairResponseDto(keyPair.base64PrivateKey(), keyPair.base64PublicKey());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "Sign data with private key", description = "Signs the provided data using EC P-256 ECDSA-SHA256 signature "
      + "with the given private key")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Data signed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid request data or malformed private key"),
      @ApiResponse(responseCode = "500", description = "Signing operation failed")
  })
  @PostMapping("/sign")
  public ResponseEntity<SignDataResponseDto> signData(
      @Valid @RequestBody SignDataRequestDto request) {
    String signature = signatureService.generateSignature(request.getData(), request.getPrivateKey());
    var response = new SignDataResponseDto(signature, request.getData(), "EC_P256");
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "Validate signature", description = "Validates an EC P-256 ECDSA-SHA256 signature "
      + "against the original data using the provided public key")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Signature validation completed"),
      @ApiResponse(responseCode = "400", description = "Invalid request data, malformed signature or public key"),
      @ApiResponse(responseCode = "500", description = "Validation operation failed")
  })
  @PostMapping("/validate")
  public ResponseEntity<ValidateSignatureResponseDto> validateSignature(
      @Valid @RequestBody ValidateSignatureRequestDto request) {
    boolean isValid = signatureService.validateSignature(
        request.getData(), request.getSignature(), request.getPublicKey());

    String message = isValid ? "Signature is valid" : "Signature is invalid";
    var response = new ValidateSignatureResponseDto(isValid, message, "EC_P256");
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "Decrypt encrypted database column value", description = "Decrypts an encrypted database column value for debugging purposes. "
      + "Accepts values in ENC:keyID:Base64(ciphertext) format and returns "
      + "comprehensive debugging metadata including decrypted plaintext, validation "
      + "information, and error details if decryption fails.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Decryption operation completed"),
      @ApiResponse(responseCode = "400", description = "Invalid request data (empty encrypted value)"),
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
            errorMessage = "Decryption failed: value returned unchanged (invalid key, corrupted data, or key not"
                + " available).";
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

    var response = new DecryptResponseDto(
        plaintext,
        isEncrypted,
        decryptionSuccessful,
        keyId,
        encryptedFormat,
        errorMessage,
        encryptionAvailable);
    return ResponseEntity.ok(response);
  }
}
