/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: OpenApiContractCustomizerTest
 * Description: Guards public security, 401/429, login ProblemDetail, and instance format.
 */

package org.ezkey.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OpenApiContractCustomizer}. */
class OpenApiContractCustomizerTest {

  @Test
  @DisplayName("Admin public paths declare empty security and do not gain a 401")
  void adminPublicPaths_emptySecurity_noUnauthorized() {
    OpenAPI openApi = new OpenAPI();
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/public/instance-info", new PathItem().get(new Operation()));
    paths.addPathItem("/api/v1/public/evaluator-signup", new PathItem().post(new Operation()));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forAdminApi().customise(openApi);

    Operation instanceInfo = openApi.getPaths().get("/api/v1/public/instance-info").getGet();
    assertThat(instanceInfo.getSecurity()).isEmpty();
    assertThat(instanceInfo.getResponses().get("401")).isNull();
    Operation signup = openApi.getPaths().get("/api/v1/public/evaluator-signup").getPost();
    assertThat(signup.getSecurity()).isEmpty();
    assertThat(signup.getResponses().get("401")).isNull();
  }

  @Test
  @DisplayName("Admin secured ops without 401 receive an empty 401")
  void adminSecuredOp_missingUnauthorized_isDocumentedEmpty() {
    OpenAPI openApi = new OpenAPI();
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/tenants", new PathItem().get(new Operation()));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forAdminApi().customise(openApi);

    ApiResponse unauthorized =
        openApi.getPaths().get("/api/v1/tenants").getGet().getResponses().get("401");
    assertThat(unauthorized).isNotNull();
    assertThat(unauthorized.getDescription()).isEqualTo("Not authenticated");
    assertThat(unauthorized.getContent()).isNull();
  }

  @Test
  @DisplayName("Auth API does not add HTTP 401 on device endpoints")
  void authApi_doesNotDocumentUnauthorizedOnDevicePosts() {
    OpenAPI openApi = new OpenAPI();
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/enrollments/bind", new PathItem().post(new Operation()));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forAuthApi().customise(openApi);

    assertThat(
            openApi.getPaths().get("/api/v1/enrollments/bind").getPost().getResponses().get("401"))
        .isNull();
  }

  @Test
  @DisplayName("Auth rate-limited POSTs document empty 429 with Retry-After")
  void authRateLimitedPost_emptyTooManyRequests() {
    OpenAPI openApi = new OpenAPI();
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/enrollments/verify", new PathItem().post(new Operation()));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forAuthApi().customise(openApi);

    ApiResponse tooMany =
        openApi.getPaths().get("/api/v1/enrollments/verify").getPost().getResponses().get("429");
    assertThat(tooMany).isNotNull();
    assertThat(tooMany.getContent()).isNull();
    assertThat(tooMany.getHeaders()).containsKey("Retry-After");
  }

  @Test
  @DisplayName("Login 401 success-DTO schema is rewritten to ProblemDetail")
  void adminLogin_errorSchema_becomesProblemDetail() {
    OpenAPI openApi = new OpenAPI();
    Operation login = new Operation();
    ApiResponses responses = new ApiResponses();
    responses.addApiResponse(
        "401",
        new ApiResponse()
            .description("Unauthorized - Invalid credentials")
            .content(
                new Content()
                    .addMediaType(
                        "*/*",
                        new MediaType()
                            .schema(
                                new Schema<>()
                                    .$ref("#/components/schemas/AdminLoginResponseDto")))));
    login.setResponses(responses);
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/admin/auth/login", new PathItem().post(login));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forAdminApi().customise(openApi);

    ApiResponse unauthorized =
        openApi.getPaths().get("/api/v1/admin/auth/login").getPost().getResponses().get("401");
    assertThat(unauthorized.getContent()).containsKey("application/problem+json");
    assertThat(unauthorized.getContent().get("application/problem+json").getSchema().get$ref())
        .isEqualTo("#/components/schemas/ProblemDetail");
  }

  @Test
  @DisplayName("Login 429 JSON body is stripped to match the empty filter response")
  void adminLogin_tooManyRequests_emptyBodyAndRetryAfter() {
    OpenAPI openApi = new OpenAPI();
    Operation login = new Operation();
    ApiResponses responses = new ApiResponses();
    responses.addApiResponse(
        "429",
        new ApiResponse()
            .description("Too Many Requests")
            .content(
                new Content()
                    .addMediaType(
                        "*/*",
                        new MediaType()
                            .schema(
                                new Schema<>()
                                    .$ref("#/components/schemas/AdminLoginResponseDto")))));
    login.setResponses(responses);
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/admin/auth/login", new PathItem().post(login));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forAdminApi().customise(openApi);

    ApiResponse tooMany =
        openApi.getPaths().get("/api/v1/admin/auth/login").getPost().getResponses().get("429");
    assertThat(tooMany.getContent()).isNull();
    assertThat(tooMany.getHeaders()).containsKey("Retry-After");
  }

  @Test
  @DisplayName("ProblemDetail instance format is uri-reference, not absolute uri")
  void problemDetailInstance_isUriReference() {
    OpenAPI openApi = new OpenAPI();
    Schema<?> problem = new Schema<>();
    problem.addProperty("instance", new StringSchema().format("uri"));
    openApi.setComponents(new Components().addSchemas("ProblemDetail", problem));
    openApi.setPaths(new Paths());

    OpenApiContractCustomizer.forAdminApi().customise(openApi);

    Schema<?> instance =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get("ProblemDetail")
                .getProperties()
                .get("instance");
    assertThat(instance.getFormat()).isEqualTo("uri-reference");
  }

  @Test
  @DisplayName("Integration secured ops receive empty 401")
  void integrationApi_documentsEmptyUnauthorized() {
    OpenAPI openApi = new OpenAPI();
    Operation create = new Operation();
    create.addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/auth-attempts", new PathItem().post(create));
    openApi.setPaths(paths);

    OpenApiContractCustomizer.forIntegrationApi().customise(openApi);

    ApiResponse unauthorized =
        openApi.getPaths().get("/api/v1/auth-attempts").getPost().getResponses().get("401");
    assertThat(unauthorized).isNotNull();
    assertThat(unauthorized.getContent()).isNull();
  }
}
