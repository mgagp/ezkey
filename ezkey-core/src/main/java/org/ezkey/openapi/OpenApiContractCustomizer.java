/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiContractCustomizer
 * Description: Aligns generated OpenAPI with public security, 401/429, and ProblemDetail instance.
 */

package org.ezkey.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Aligns generated OpenAPI with the live HTTP contract for public operations, unauthenticated
 * entry-point 401s, filter-emitted 429s, and RFC 9457 {@code instance}.
 *
 * <p>Springdoc applies the module-level security requirement to every operation, including public
 * ones, unless the operation sets {@code security: []}. {@code @Operation(security = {})} does not
 * emit that empty array, so public paths are forced here. Login-family error responses inherit the
 * success DTO (string {@code status}) unless rewritten to ProblemDetail. Servlet rate-limit filters
 * return an empty 429 plus {@code Retry-After}, not JSON.
 *
 * @since 2026
 */
public final class OpenApiContractCustomizer implements OpenApiCustomizer {

  private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final List<String> PROBLEM_DETAIL_ERROR_CODES =
      List.of("400", "401", "408", "500");

  private final Set<String> publicPaths;
  private final Set<String> emptyBodyRateLimitedPostPaths;
  private final Set<String> problemDetailErrorPostPaths;
  private final boolean documentUnauthorizedOnSecuredOps;

  /**
   * Creates a contract customizer for one API module.
   *
   * @param publicPaths paths that must declare {@code security: []} (all methods)
   * @param emptyBodyRateLimitedPostPaths POST paths whose 429 is an empty filter body
   * @param problemDetailErrorPostPaths POST paths whose 4xx/5xx (except 429) are ProblemDetail
   * @param documentUnauthorizedOnSecuredOps whether to add empty 401 on non-public operations
   */
  public OpenApiContractCustomizer(
      Set<String> publicPaths,
      Set<String> emptyBodyRateLimitedPostPaths,
      Set<String> problemDetailErrorPostPaths,
      boolean documentUnauthorizedOnSecuredOps) {
    this.publicPaths = Set.copyOf(publicPaths);
    this.emptyBodyRateLimitedPostPaths = Set.copyOf(emptyBodyRateLimitedPostPaths);
    this.problemDetailErrorPostPaths = Set.copyOf(problemDetailErrorPostPaths);
    this.documentUnauthorizedOnSecuredOps = documentUnauthorizedOnSecuredOps;
  }

  /**
   * Admin API: public instance-info and evaluator signup; login-family 401/429; empty 401
   * elsewhere.
   *
   * @return admin contract customizer
   */
  public static OpenApiContractCustomizer forAdminApi() {
    Set<String> loginFamily =
        Set.of("/api/v1/admin/auth/login", "/api/v1/admin/auth/passwordless-wait");
    return new OpenApiContractCustomizer(
        Set.of("/api/v1/public/instance-info", "/api/v1/public/evaluator-signup"),
        loginFamily,
        loginFamily,
        true);
  }

  /**
   * Auth API: public instance-info; empty 429 on rate-limited device POSTs; no HTTP 401 (device
   * endpoints are signature-in-body, not Bearer).
   *
   * @return auth contract customizer
   */
  public static OpenApiContractCustomizer forAuthApi() {
    return new OpenApiContractCustomizer(
        Set.of("/api/v1/public/instance-info"),
        Set.of(
            "/api/v1/enrollments/bind",
            "/api/v1/enrollments/verify",
            "/api/v1/enrollments/instance-info",
            "/api/v1/auth-attempts/pending",
            "/api/v1/auth-attempts/respond"),
        Set.of(),
        false);
  }

  /**
   * Integration API: empty 401 from {@code HttpStatusEntryPoint} on the three authenticated ops.
   *
   * @return integration contract customizer
   */
  public static OpenApiContractCustomizer forIntegrationApi() {
    return new OpenApiContractCustomizer(Set.of(), Set.of(), Set.of(), true);
  }

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi == null) {
      return;
    }
    patchProblemDetailInstance(openApi);
    if (openApi.getPaths() == null) {
      return;
    }
    openApi
        .getPaths()
        .forEach(
            (path, item) -> {
              if (path == null || item == null) {
                return;
              }
              item.readOperationsMap()
                  .forEach((method, operation) -> customiseOperation(path, method, operation));
            });
  }

  private void customiseOperation(String path, PathItem.HttpMethod method, Operation operation) {
    if (operation == null) {
      return;
    }
    if (publicPaths.contains(path)) {
      operation.setSecurity(new ArrayList<>());
    }
    if (method == PathItem.HttpMethod.POST && problemDetailErrorPostPaths.contains(path)) {
      rewriteExistingErrorsToProblemDetail(operation);
    }
    ApiResponses responses = ensureResponses(operation);
    if (documentUnauthorizedOnSecuredOps
        && !isPublicOperation(operation)
        && responses.get("401") == null) {
      responses.addApiResponse("401", new ApiResponse().description("Not authenticated"));
    }
    if (method == PathItem.HttpMethod.POST && emptyBodyRateLimitedPostPaths.contains(path)) {
      applyEmptyTooManyRequests(responses);
    }
  }

  private static void rewriteExistingErrorsToProblemDetail(Operation operation) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      return;
    }
    for (String code : PROBLEM_DETAIL_ERROR_CODES) {
      ApiResponse existing = responses.get(code);
      if (existing == null) {
        continue;
      }
      String description = existing.getDescription();
      if (description == null || description.isBlank()) {
        description = "RFC 9457 Problem Details";
      }
      responses.addApiResponse(code, problemDetailResponse(description));
    }
  }

  private static void applyEmptyTooManyRequests(ApiResponses responses) {
    ApiResponse existing = responses.get("429");
    ApiResponse tooMany = existing != null ? existing : new ApiResponse();
    if (tooMany.getDescription() == null || tooMany.getDescription().isBlank()) {
      tooMany.setDescription(
          "Too many requests. Retry-After is seconds until the rate-limit budget refills.");
    }
    tooMany.setContent(null);
    tooMany.addHeaderObject("Retry-After", retryAfterHeader());
    responses.addApiResponse("429", tooMany);
  }

  private static ApiResponse problemDetailResponse(String description) {
    MediaType mediaType = new MediaType().schema(new Schema<>().$ref(PROBLEM_DETAIL_REF));
    return new ApiResponse()
        .description(description)
        .content(new Content().addMediaType(PROBLEM_JSON, mediaType));
  }

  private static Header retryAfterHeader() {
    return new Header()
        .description("Seconds until retry is allowed")
        .schema(new IntegerSchema().format("int32"));
  }

  private static ApiResponses ensureResponses(Operation operation) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }
    return responses;
  }

  private static boolean isPublicOperation(Operation operation) {
    return operation.getSecurity() != null && operation.getSecurity().isEmpty();
  }

  /**
   * RFC 9457 {@code instance} is a URI-reference. Ezkey handlers put the request path there (and
   * also as extension {@code path}), so {@code format: uri} (absolute) is dishonest.
   */
  private static void patchProblemDetailInstance(OpenAPI openApi) {
    Components components = openApi.getComponents();
    if (components == null || components.getSchemas() == null) {
      return;
    }
    Schema<?> problem = components.getSchemas().get("ProblemDetail");
    if (problem == null || problem.getProperties() == null) {
      return;
    }
    Object instanceProperty = problem.getProperties().get("instance");
    if (!(instanceProperty instanceof Schema<?> instance)) {
      return;
    }
    instance.setFormat("uri-reference");
    instance.setDescription(
        "URI-reference of the occurrence. Ezkey uses the request path (relative), matching RFC"
            + " 9457; it is not an absolute URI.");
  }
}
