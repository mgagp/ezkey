/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: UserMapping
 * Description: User mapping data structure for JSON file.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Root structure for users mapping JSON file.
 *
 * @param users list of user mappings
 */
public record UserMapping(@JsonProperty("users") List<UserEntry> users) {

  /**
   * Individual user entry mapping username to enrollment.
   *
   * @param username the username
   * @param enrollmentId the enrollment ID
   * @param displayName optional display name
   */
  public record UserEntry(
      String username, @JsonProperty("enrollmentId") Integer enrollmentId, String displayName) {}
}