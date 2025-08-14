
package org.ezkey.sim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO containing pending authentication attempt details")
public class ProofTokenResponseDto {

    @Schema(description = "A proof token",example = "eyJhbGciOiJSUzI1NiJ9...",requiredMode = RequiredMode.REQUIRED)
    private String proofToken;

    public ProofTokenResponseDto(String proofToken){
        this.proofToken = proofToken;
    }

    public String getProofToken() {
        return proofToken;
    }

    public void setProofToken(String proofToken) {
        this.proofToken = proofToken;
    }

}