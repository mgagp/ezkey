package org.ezkey.demo.device.service;

import java.time.Duration;

import org.ezkey.demodevice.generated.dto.EnrollmentBindResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyRequestDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyResponseDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

/**
 * Service to call Ezkey Auth API endpoints needed by the demo device.
 * Uses generated DTOs for type safety and better maintainability.
 *
 * @since 2025
 */
@Service
public class AuthApiService {

    private static final Logger logger = LoggerFactory.getLogger(AuthApiService.class);

    private final WebClient authClient;

    public AuthApiService(@Qualifier("ezkeyAuthApiClient") WebClient authClient){
        this.authClient = authClient;
    }

    /**
     * Calls GET /api/v1/enrollment/bind/{id} to start enrollment binding.
     *
     * @param enrollmentId id to bind
     * @return typed response DTO with integrationPublicKey, enrollmentProofToken, etc.
     */
    public Mono<EnrollmentBindResponseDto> bind(Integer enrollmentId) {
        String uri = String.format("/api/v1/enrollments/bind/%d", enrollmentId);

        Mono<EnrollmentBindResponseDto> responseMono = authClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(EnrollmentBindResponseDto.class)
                .timeout(Duration.ofSeconds(15));

        Mono<EnrollmentBindResponseDto> errorHandledMono = responseMono
                .doOnError(e -> logger.error("Bind failed for {}", enrollmentId, e))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
                .onErrorResume(Exception.class, ex -> Mono.error(ex));

        return errorHandledMono;
    }

    /**
     * Calls POST /api/v1/enrollments/verify.
     *
     * @param requestDto typed request DTO with enrollmentId, challengeResponse, devicePublicKey, enrollmentProofTokenSigned
     * @return typed response DTO
     */
    public Mono<EnrollmentVerifyResponseDto> verify(EnrollmentVerifyRequestDto requestDto) {
        String uri = "/api/v1/enrollments/verify";

        WebClient.RequestBodySpec requestSpec = authClient.post().uri(uri);
        WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.bodyValue(requestDto);

        Mono<EnrollmentVerifyResponseDto> responseMono = headersSpec
                .retrieve()
                .bodyToMono(EnrollmentVerifyResponseDto.class)
                .timeout(Duration.ofSeconds(15));

        Mono<EnrollmentVerifyResponseDto> errorHandledMono = responseMono
                .doOnSuccess(response -> logger.info("Verify API response: {}", response))
                .doOnError(e -> logger.error("Verify failed for request: {}", requestDto, e))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
                .onErrorResume(Exception.class, ex -> Mono.error(ex));

        return errorHandledMono;
    }
}
