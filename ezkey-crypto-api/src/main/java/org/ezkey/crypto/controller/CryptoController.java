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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.ezkey.crypto.dto.ECP256KeyPairResponseDto;
import org.ezkey.crypto.dto.ProofTokenResponseDto;
import org.ezkey.crypto.dto.SignDataRequestDto;
import org.ezkey.crypto.dto.SignDataResponseDto;
import org.ezkey.crypto.dto.ValidateSignatureRequestDto;
import org.ezkey.crypto.dto.ValidateSignatureResponseDto;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  private final SignatureService signatureService;

  @Autowired
  public CryptoController(SignatureService signatureService) {
    this.signatureService = signatureService;
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
        @ApiResponse(responseCode = "200", 
            description = "EC P-256 key pair generated successfully"),
        @ApiResponse(responseCode = "500", 
            description = "Key generation failed")
      })
  @GetMapping("/keypair")
  public ResponseEntity<ECP256KeyPairResponseDto> generateKeyPair() {
    ECP256KeyPair keyPair = signatureService.generateECP256KeyPair();
    var response =
        new ECP256KeyPairResponseDto(
            keyPair.base64PrivateKey(), keyPair.base64PublicKey());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Sign data with private key",
      description =
          "Signs the provided data using EC P-256 ECDSA-SHA256 signature " 
          +
          "with the given private key")
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
    String signature =
        signatureService.generateSignature(request.getData(), request.getPrivateKey());
    var response = new SignDataResponseDto(signature, request.getData(), "EC_P256");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Validate signature",
      description =
          "Validates an EC P-256 ECDSA-SHA256 signature " 
          + 
          "against the original data using the provided public key")
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
}
