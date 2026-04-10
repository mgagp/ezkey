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
 * @since 2025
 */
@Service
public class AuthApiService {

  private static final Logger logger = LoggerFactory.getLogger(AuthApiService.class);

  private final WebClient authClient;

  public AuthApiService(@Qualifier("ezkeyAuthApiClient") WebClient authClient) {
    this.authClient = authClient;
  }

  /**
   * Calls POST /api/v1/enrollments/bind to start enrollment binding with proof token.
   *
   * @param enrollmentId id to bind
   * @param enrollmentProofToken proof token for authentication
   * @return typed response DTO with integrationPublicKey, enrollmentProofToken, etc.
   */
  public Mono<EnrollmentBindResponseDto> bind(Integer enrollmentId, String enrollmentProofToken) {
    String uri = "/api/v1/enrollments/bind";

    // Create request DTO
    EnrollmentBindRequestDto requestDto =
        new EnrollmentBindRequestDto()
            .enrollmentId(enrollmentId)
            .enrollmentProofToken(enrollmentProofToken);

    WebClient.RequestBodySpec requestSpec = authClient.post().uri(uri);
    WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.bodyValue(requestDto);

    Mono<EnrollmentBindResponseDto> responseMono =
        headersSpec
            .retrieve()
            .bodyToMono(EnrollmentBindResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    Mono<EnrollmentBindResponseDto> errorHandledMono =
        responseMono
            .doOnSuccess(
                response -> logger.info("Bind API completed for enrollment {}", enrollmentId))
            .doOnError(e -> logger.error("Bind failed for enrollment {}", enrollmentId, e))
            .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
            .onErrorResume(Exception.class, ex -> Mono.error(ex));

    return errorHandledMono;
  }

  /**
   * Calls POST /api/v1/enrollments/verify.
   *
   * @param requestDto typed request DTO with enrollmentId, challengeResponse, devicePublicKey,
   *     enrollmentProofTokenSigned
   * @return typed response DTO
   */
  public Mono<EnrollmentVerifyResponseDto> verify(EnrollmentVerifyRequestDto requestDto) {
    String uri = "/api/v1/enrollments/verify";

    WebClient.RequestBodySpec requestSpec = authClient.post().uri(uri);
    WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.bodyValue(requestDto);

    Mono<EnrollmentVerifyResponseDto> responseMono =
        headersSpec
            .retrieve()
            .bodyToMono(EnrollmentVerifyResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    Mono<EnrollmentVerifyResponseDto> errorHandledMono =
        responseMono
            .doOnSuccess(
                response ->
                    logger.info(
                        "Verify API completed for enrollment {}", requestDto.getEnrollmentId()))
            .doOnError(
                e ->
                    logger.error(
                        "Verify failed for enrollment {}", requestDto.getEnrollmentId(), e))
            .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
            .onErrorResume(Exception.class, ex -> Mono.error(ex));

    return errorHandledMono;
  }

  /**
   * Calls POST /api/v1/auth-attempts/pending to check for pending authentication attempts. Updated
   * to use enrollmentProofToken for secure enrollment identification.
   *
   * @param requestDto typed request DTO with enrollmentId, enrollmentProofToken, deviceProofToken,
   *     deviceProofTokenSigned
   * @return typed response DTO with auth attempt details or null if no pending attempt
   */
  public Mono<AuthAttemptPendingResponseDto> pending(AuthAttemptPendingRequestDto requestDto) {
    String uri = "/api/v1/auth-attempts/pending";

    WebClient.RequestBodySpec requestSpec = authClient.post().uri(uri);
    WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.bodyValue(requestDto);

    Mono<AuthAttemptPendingResponseDto> responseMono =
        headersSpec
            .retrieve()
            .bodyToMono(AuthAttemptPendingResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    Mono<AuthAttemptPendingResponseDto> errorHandledMono =
        responseMono
            .doOnSuccess(
                response ->
                    logger.info(
                        "Pending API completed for enrollment {}", requestDto.getEnrollmentId()))
            .doOnError(
                e ->
                    logger.error(
                        "Pending failed for enrollment {}", requestDto.getEnrollmentId(), e))
            .onErrorResume(
                WebClientResponseException.class,
                ex -> {
                  if (ex.getStatusCode().value() == 204) {
                    // No pending attempts
                    return Mono.empty();
                  }
                  return Mono.error(ex);
                })
            .onErrorResume(Exception.class, ex -> Mono.error(ex));

    return errorHandledMono;
  }

  /**
   * Calls POST /api/v1/auth-attempts/respond to submit authentication response.
   *
   * @param requestDto typed request DTO with authAttemptId, approved, responseSignature, etc.
   * @return typed response DTO with result
   */
  public Mono<AuthAttemptRespondResponseDto> respond(AuthAttemptRespondRequestDto requestDto) {
    String uri = "/api/v1/auth-attempts/respond";

    WebClient.RequestBodySpec requestSpec = authClient.post().uri(uri);
    WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.bodyValue(requestDto);

    Mono<AuthAttemptRespondResponseDto> responseMono =
        headersSpec
            .retrieve()
            .bodyToMono(AuthAttemptRespondResponseDto.class)
            .timeout(Duration.ofSeconds(15));

    Mono<AuthAttemptRespondResponseDto> errorHandledMono =
        responseMono
            .doOnSuccess(
                response ->
                    logger.info(
                        "Respond API completed for authAttemptId {}",
                        requestDto.getAuthAttemptId()))
            .doOnError(
                e ->
                    logger.error(
                        "Respond failed for authAttemptId {}", requestDto.getAuthAttemptId(), e))
            .onErrorResume(WebClientResponseException.class, ex -> Mono.error(ex))
            .onErrorResume(Exception.class, ex -> Mono.error(ex));

    return errorHandledMono;
  }
}
