/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Patrick B2/M3 on #898: strict {@code ttlSeconds} JSON and required {@code challengeRequested}.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("AuthAttemptCreateRequestDto strict ttlSeconds + challengeRequested (#895)")
class AuthAttemptCreateRequestDtoStrictTtlTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("ttlSeconds=20.5 (float) → Jackson fails")
  void ttlSeconds_float_rejected() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    """
                    {"enrollmentId":1,"challengeRequested":false,"ttlSeconds":20.5}
                    """,
                    AuthAttemptCreateRequestDto.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("ttlSeconds=\"20\" (string) → Jackson fails")
  void ttlSeconds_string_rejected() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    """
                    {"enrollmentId":1,"challengeRequested":false,"ttlSeconds":"20"}
                    """,
                    AuthAttemptCreateRequestDto.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("ttlSeconds=true (boolean) → Jackson fails")
  void ttlSeconds_boolean_rejected() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    """
                    {"enrollmentId":1,"challengeRequested":false,"ttlSeconds":true}
                    """,
                    AuthAttemptCreateRequestDto.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("ttlSeconds=null (explicit) → Jackson fails")
  void ttlSeconds_explicitNull_rejected() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    """
                    {"enrollmentId":1,"challengeRequested":false,"ttlSeconds":null}
                    """,
                    AuthAttemptCreateRequestDto.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("Absent ttlSeconds → null (server default path)")
  void ttlSeconds_absent_remainsNull() throws Exception {
    AuthAttemptCreateRequestDto dto =
        mapper.readValue(
            """
            {"enrollmentId":1,"challengeRequested":false}
            """,
            AuthAttemptCreateRequestDto.class);
    assertThat(dto.ttlSeconds()).isNull();
    assertThat(dto.challengeRequested()).isFalse();
    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  @DisplayName("ttlSeconds integer 75 → accepted")
  void ttlSeconds_integer_accepted() throws Exception {
    AuthAttemptCreateRequestDto dto =
        mapper.readValue(
            """
            {"enrollmentId":1,"challengeRequested":true,"ttlSeconds":75}
            """,
            AuthAttemptCreateRequestDto.class);
    assertThat(dto.ttlSeconds()).isEqualTo(75);
    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  @DisplayName("challengeRequested omitted → @NotNull violation (M3, pre-#895 contract)")
  void challengeRequested_absent_failsBeanValidation() throws Exception {
    AuthAttemptCreateRequestDto dto =
        mapper.readValue(
            """
            {"enrollmentId":1,"ttlSeconds":30}
            """,
            AuthAttemptCreateRequestDto.class);
    assertThat(dto.challengeRequested()).isNull();
    Set<ConstraintViolation<AuthAttemptCreateRequestDto>> violations = validator.validate(dto);
    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().equals("challengeRequested"));
  }
}
