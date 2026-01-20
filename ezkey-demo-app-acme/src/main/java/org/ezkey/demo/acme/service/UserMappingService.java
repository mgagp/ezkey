/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: UserMappingService
 * Description: Loads and hot-reloads user mapping from JSON file.
 */

package org.ezkey.demo.acme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.dto.UserMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for loading and hot-reloading user mapping from JSON file.
 *
 * <p>This service maintains a mapping of username to enrollmentId by reading from an external JSON
 * file. It supports hot-reload by checking the file timestamp periodically and reloading if changed.
 *
 * <p>This pattern aligns with demo-device's EnrollmentStoreService for consistency in Docker test
 * scenarios.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class UserMappingService {

  private static final Logger logger = LoggerFactory.getLogger(UserMappingService.class);

  private final AcmeProperties properties;
  private final ObjectMapper objectMapper;
  private final Path usersFile;
  private final Map<String, UserMapping.UserEntry> userMap = new HashMap<>();
  private long lastModified = 0;

  public UserMappingService(AcmeProperties properties) {
    this.properties = properties;
    this.objectMapper = new ObjectMapper();
    this.usersFile = Paths.get(properties.getUsers().getFile());

    // Initial load
    loadUsersFile();
  }

  /**
   * Looks up enrollment ID for a given username.
   *
   * @param username the username to lookup
   * @return Optional containing UserEntry if found, empty otherwise
   */
  public Optional<UserMapping.UserEntry> findByUsername(String username) {
    return Optional.ofNullable(userMap.get(username));
  }

  /**
   * Checks if a user exists in the mapping.
   *
   * @param username the username to check
   * @return true if user exists, false otherwise
   */
  public boolean userExists(String username) {
    return userMap.containsKey(username);
  }

  /**
   * Scheduled task to check file timestamp and reload if changed.
   *
   * <p>Runs periodically based on configured check interval. If interval is 0, hot-reload is
   * disabled.
   */
  @Scheduled(fixedDelayString = "${ezkey.users.file.check-interval:5}000")
  public void checkAndReload() {
    Integer interval = properties.getUsers().getCheckInterval();
    if (interval == null || interval <= 0) {
      // Hot-reload disabled
      return;
    }

    try {
      if (!Files.exists(usersFile)) {
        logger.warn("Users file does not exist: {}", usersFile);
        return;
      }

      long currentModified = Files.getLastModifiedTime(usersFile).toMillis();
      if (currentModified > lastModified) {
        logger.info("Users file changed, reloading: {}", usersFile);
        loadUsersFile();
      }
    } catch (IOException e) {
      logger.error("Error checking users file timestamp: {}", usersFile, e);
    }
  }

  /**
   * Loads user mapping from JSON file.
   *
   * <p>Reads the file, parses JSON, and updates internal map. Logs errors but does not throw to
   * allow application startup even if file is missing (for development scenarios).
   */
  private void loadUsersFile() {
    try {
      if (!Files.exists(usersFile)) {
        logger.warn("Users file not found: {}. User mapping will be empty.", usersFile);
        userMap.clear();
        lastModified = 0;
        return;
      }

      byte[] json = Files.readAllBytes(usersFile);
      UserMapping mapping = objectMapper.readValue(json, UserMapping.class);

      // Clear and rebuild map
      userMap.clear();
      if (mapping.users() != null) {
        for (UserMapping.UserEntry entry : mapping.users()) {
          if (entry.username() != null && entry.enrollmentId() != null) {
            userMap.put(entry.username(), entry);
          }
        }
      }

      lastModified = Files.getLastModifiedTime(usersFile).toMillis();
      logger.info(
          "Loaded {} users from mapping file: {}", userMap.size(), usersFile);

    } catch (IOException e) {
      logger.error("Failed to load users file: {}", usersFile, e);
      // Don't throw - allow app to start even if file is missing
    }
  }
}