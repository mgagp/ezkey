/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: SimController
 * Description: REST controller for simulation API v1 providing crypto services for postman tests.
 */

package org.ezkey.sim.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.ezkey.signature.RsaKeyPair;
import org.ezkey.signature.SignatureService;
import org.ezkey.sim.dto.ProofTokenResponseDto;
import org.ezkey.sim.dto.RsaKeyPairResponseDto;
import org.ezkey.sim.dto.SignDataRequestDto;
import org.ezkey.sim.dto.SignDataResponseDto;
import org.ezkey.sim.dto.ValidateSignatureRequestDto;
import org.ezkey.sim.dto.ValidateSignatureResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Simulation API", description = "Crypto services for postman and testing tools")
@RestController
@RequestMapping("/api/v1/sim")
public class SimController {

  private final SignatureService signatureService;

  @Autowired
  public SimController(SignatureService signatureService) {
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
      summary = "Generate RSA key pair",
      description =
          "Generates a new RSA key pair for use in device simulation. Default key size is 2048 bits.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "RSA key pair generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid key size parameter"),
        @ApiResponse(responseCode = "500", description = "Key generation failed")
      })
  @GetMapping("/keypair")
  public ResponseEntity<RsaKeyPairResponseDto> generateKeyPair(
      @Parameter(description = "RSA key size in bits (default: 2048, min: 1024, max: 4096)")
          @RequestParam(name = "keySize", defaultValue = "2048")
          int keySize) {
    if (keySize < 1024 || keySize > 4096) {
      throw new IllegalArgumentException("Key size must be between 1024 and 4096 bits");
    }

    RsaKeyPair keyPair = signatureService.generateRsaKeyPair(keySize);
    var response =
        new RsaKeyPairResponseDto(keyPair.base64PrivateKey(), keyPair.base64PublicKey(), keySize);
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Sign data with private key",
      description = "Signs the provided data using RSA-SHA256 signature with the given private key")
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
    var response = new SignDataResponseDto(signature, request.getData(), "SHA256withRSA");
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Validate signature",
      description = "Validates a signature against the original data using the provided public key")
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
    var response = new ValidateSignatureResponseDto(isValid, message, "SHA256withRSA");
    return ResponseEntity.ok(response);
  }
}
