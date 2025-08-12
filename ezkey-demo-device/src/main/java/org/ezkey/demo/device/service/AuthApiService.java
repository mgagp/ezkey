package org.ezkey.demo.device.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

/**
 * Service to call Ezkey Auth API endpoints needed by the demo device.
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
     * @return response map (integrationPublicKey, enrollmentProofToken, ...)
     */
    public Mono<Map> bindOrig(Integer enrollmentId) {
        return authClient.get().uri("/api/v1/enrollments/bind/{id}",enrollmentId).retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(15))
                .doOnError(e -> logger.error("Bind failed for {}",enrollmentId,e)).onErrorResume(WebClientResponseException.class,ex -> Mono.error(ex))
                .onErrorResume(Exception.class,ex -> Mono.error(ex));
    }

    public Mono<Map> bind(Integer enrollmentId) {
        String uri = String.format("/api/v1/enrollments/bind/%d",enrollmentId);

        Mono<Map> responseMono = authClient.get().uri(uri).retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(15));

        Mono<Map> errorHandledMono = responseMono.doOnError(e -> logger.error("Bind failed for {}",enrollmentId,e))
                .onErrorResume(WebClientResponseException.class,ex -> Mono.error(ex)).onErrorResume(Exception.class,ex -> Mono.error(ex));

        return errorHandledMono;
    }

    /**
     * Calls POST /api/v1/enrollments/verify.
     *
     * @param payload body map including enrollmentId, challengeResponse, devicePublicKey, enrollmentProofTokenSigned
     * @return response as a map
     */
    public Mono<Map> verifyOrig(Map<String, Object> payload) {
        return authClient.post().uri("/api/v1/enrollments/verify").bodyValue(payload).retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(15))
                .doOnError(e -> logger.error("Verify failed payload {}",payload,e)).onErrorResume(WebClientResponseException.class,ex -> Mono.error(ex))
                .onErrorResume(Exception.class,ex -> Mono.error(ex));
    }

    public Mono<Map> verify(Map<String, Object> payload) {
        String uri = "/api/v1/enrollments/verify";

        WebClient.RequestBodySpec requestSpec = authClient.post().uri(uri);

        WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.bodyValue(payload);

        Mono<Map> responseMono = headersSpec.retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(15));

        Mono<Map> errorHandledMono = responseMono.doOnError(e -> logger.error("Verify failed payload {}",payload,e))
                .onErrorResume(WebClientResponseException.class,ex -> Mono.error(ex)).onErrorResume(Exception.class,ex -> Mono.error(ex));

        return errorHandledMono;
    }
}
