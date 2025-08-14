/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: SimController
 * Description: REST controller for mobile authentication attempt API v1.
 */

package org.ezkey.sim.controller;

import org.ezkey.signature.SignatureService;
import org.ezkey.sim.dto.ProofTokenResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sim")
public class SimController {

    private final SignatureService signatureService;

    @Autowired
    public SimController(SignatureService signatureService){
        this.signatureService = signatureService;
    }

    @GetMapping("/prooftoken")
    public ResponseEntity<ProofTokenResponseDto> prooftoken() {
        var reponse = new ProofTokenResponseDto(signatureService.generateProofToken());
        return ResponseEntity.ok(reponse);
    }

}
