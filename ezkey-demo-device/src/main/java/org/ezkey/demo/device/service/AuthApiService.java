package org.ezkey.demo.device.service;

import java.time.Duration;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingResponseDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentBindRequestDto;
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
 * Service to call Ezkey Auth API endpoints needed by the demo device. Uses generated DTOs for type
 * safety and better maintainability.
 *
 * <p>Each method accepts an optional {@code authApiBaseUrl} override for per-enrollment routing
 * (QR {@code authUrl} parity with mobile). When null or blank, the configured default client is
 * used.
 *
 * @since 2025
 */
@Service
public class AuthApiService {

  private static final Logger logger = LoggerFactory.getLogger(AuthApiService.class);

  private final WebClient defaultClient;

  public AuthApiService(@Qualifier("ezkeyAuthApiClient") WebClient defaultClient) {
    this.defaultClient = defaultClient;
  }

  /**
   * Calls POST /api/v1/enrollments/bind to start enrollment binding with proof token.
   *
   * @param enrollmentId id to bind
   * @param enrollmentProofToken proof token for authentication
   * @param authApiBaseUrl optional per-enrollment Auth API base URL override
   * @return typed response DTO with integrationPublicKey, enrollmentProofToken, etc.
   */
  public Mono<EnrollmentBindResponseDto> bind(
      Integer enrollmentId, String enrollmentProofToken, String authApiBaseUrl) {
    String uri = "/api/v1/enrollments/bind";

    EnrollmentBindRequestDto requestDto =
        new EnrollmentBindRequestDto()
            .enrollmentId(enrollmentId)
            .enrollmentProofToken(enrollmentProofToken);

    WebClient client = clientFor(authApiBaseUrl);
    Mono<EnrollmentBindResponseDto> responseMono =
        client
            .post()
            .uri(uri)
            .bodyValue(requestDto)
            .retrieve()
            .bodyToMono(EnrollmentBindResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    return responseMono
        .doOnSuccess(response -> logger.info("Bind API completed for enrollment {}", enrollmentId))
        .doOnError(e -> logger.error("Bind failed for enrollment {}", enrollmentId, e))
        .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
        .onErrorResume(Exception.class, ex -> Mono.error(ex));
  }

  /**
   * Calls POST /api/v1/enrollments/verify.
   *
   * @param requestDto typed request DTO with enrollmentId, challengeResponse, devicePublicKey,
   *     enrollmentProofTokenSigned
   * @param authApiBaseUrl optional per-enrollment Auth API base URL override
   * @return typed response DTO
   */
  public Mono<EnrollmentVerifyResponseDto> verify(
      EnrollmentVerifyRequestDto requestDto, String authApiBaseUrl) {
    String uri = "/api/v1/enrollments/verify";

    WebClient client = clientFor(authApiBaseUrl);
    Mono<EnrollmentVerifyResponseDto> responseMono =
        client
            .post()
            .uri(uri)
            .bodyValue(requestDto)
            .retrieve()
            .bodyToMono(EnrollmentVerifyResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    return responseMono
        .doOnSuccess(
            response ->
                logger.info("Verify API completed for enrollment {}", requestDto.getEnrollmentId()))
        .doOnError(
            e -> logger.error("Verify failed for enrollment {}", requestDto.getEnrollmentId(), e))
        .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
        .onErrorResume(Exception.class, ex -> Mono.error(ex));
  }

  /**
   * Calls POST /api/v1/auth-attempts/pending to check for pending authentication attempts.
   *
   * @param requestDto typed request DTO with enrollmentId, enrollmentProofToken, deviceProofToken,
   *     deviceProofTokenSigned
   * @param authApiBaseUrl optional per-enrollment Auth API base URL override
   * @return typed response DTO with auth attempt details or empty when no pending attempt
   */
  public Mono<AuthAttemptPendingResponseDto> pending(
      AuthAttemptPendingRequestDto requestDto, String authApiBaseUrl) {
    String uri = "/api/v1/auth-attempts/pending";

    WebClient client = clientFor(authApiBaseUrl);
    Mono<AuthAttemptPendingResponseDto> responseMono =
        client
            .post()
            .uri(uri)
            .bodyValue(requestDto)
            .retrieve()
            .bodyToMono(AuthAttemptPendingResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    return responseMono
        .doOnSuccess(
            response ->
                logger.info("Pending API completed for enrollment {}", requestDto.getEnrollmentId()))
        .doOnError(
            e -> logger.error("Pending failed for enrollment {}", requestDto.getEnrollmentId(), e))
        .onErrorResume(
            WebClientResponseException.class,
            ex -> {
              if (ex.getStatusCode().value() == 204) {
                return Mono.empty();
              }
              return Mono.error(ex);
            })
        .onErrorResume(Exception.class, ex -> Mono.error(ex));
  }

  /**
   * Calls POST /api/v1/auth-attempts/respond to submit authentication response.
   *
   * @param requestDto typed request DTO with authAttemptId, approved, responseSignature, etc.
   * @param authApiBaseUrl optional per-enrollment Auth API base URL override
   * @return typed response DTO with result
   */
  public Mono<AuthAttemptRespondResponseDto> respond(
      AuthAttemptRespondRequestDto requestDto, String authApiBaseUrl) {
    String uri = "/api/v1/auth-attempts/respond";

    WebClient client = clientFor(authApiBaseUrl);
    Mono<AuthAttemptRespondResponseDto> responseMono =
        client
            .post()
            .uri(uri)
            .bodyValue(requestDto)
            .retrieve()
            .bodyToMono(AuthAttemptRespondResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    return responseMono
        .doOnSuccess(
            response ->
                logger.info(
                    "Respond API completed for authAttemptId {}", requestDto.getAuthAttemptId()))
        .doOnError(
            e ->
                logger.error(
                    "Respond failed for authAttemptId {}", requestDto.getAuthAttemptId(), e))
        .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
        .onErrorResume(Exception.class, ex -> Mono.error(ex));
  }

  private WebClient clientFor(String authApiBaseUrl) {
    if (authApiBaseUrl == null || authApiBaseUrl.isBlank()) {
      return defaultClient;
    }
    return WebClient.builder().baseUrl(authApiBaseUrl).build();
  }
}
