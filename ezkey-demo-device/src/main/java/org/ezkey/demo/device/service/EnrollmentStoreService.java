package org.ezkey.demo.device.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simple filesystem-backed store for enrollments for the demo device. Each enrollment is stored as
 * a separate JSON file.
 *
 * @since 2025
 */
@Service
public class EnrollmentStoreService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentStoreService.class);

  private final ObjectMapper objectMapper;
  private final Path rootDir;

  public EnrollmentStoreService() {
    this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    this.rootDir = Paths.get("data", "enrollments");
    try {
      Files.createDirectories(rootDir);
    } catch (IOException e) {
      logger.error("Failed to create enrollment data directory {}", rootDir, e);
      throw new IllegalStateException("Cannot initialize enrollment store", e);
    }
  }

  public void save(Record record) {
    try {
      if (record.createdAt() == null) {
        record =
            new Record(
                record.enrollmentId(),
                record.integrationId(),
                record.enrollmentName(),
                record.enrollmentUrl(),
                record.integrationPublicKey(),
                record.enrollmentProofToken(),
                record.devicePublicKey(),
                record.devicePrivateKey(),
                record.authAttemptChallengeRequired(),
                record.deviceLabel(),
                Instant.now().toString(),
                record.integrationName(),
                record.integrationDescription(),
                record.integrationLogo());
      }
      Path file = rootDir.resolve(record.enrollmentId() + ".json");
      byte[] json = objectMapper.writeValueAsBytes(record);
      Files.write(file, json);
    } catch (Exception e) {
      logger.error("Failed to save enrollment {}", record.enrollmentId(), e);
      throw new IllegalStateException("Cannot save enrollment", e);
    }
  }

  public Optional<Record> load(Integer enrollmentId) {
    try {
      Path file = rootDir.resolve(enrollmentId + ".json");
      if (!Files.exists(file)) return Optional.empty();
      byte[] json = Files.readAllBytes(file);
      return Optional.of(objectMapper.readValue(json, Record.class));
    } catch (Exception e) {
      logger.error("Failed to load enrollment {}", enrollmentId, e);
      return Optional.empty();
    }
  }

  public List<Record> list() {
    List<Record> items = new ArrayList<>();
    try {
      if (!Files.exists(rootDir)) return items;
      Files.list(rootDir)
          .filter(p -> p.getFileName().toString().endsWith(".json"))
          .forEach(
              p -> {
                try {
                  byte[] json = Files.readAllBytes(p);
                  items.add(objectMapper.readValue(json, Record.class));
                } catch (IOException ex) {
                  logger.warn("Skipping unreadable enrollment file {}", p);
                }
              });
    } catch (IOException e) {
      logger.error("Error listing enrollments", e);
    }
    return items;
  }

  public void delete(Integer enrollmentId) {
    try {
      Path file = rootDir.resolve(enrollmentId + ".json");
      Files.deleteIfExists(file);
    } catch (IOException e) {
      logger.error("Failed to delete enrollment {}", enrollmentId, e);
    }
  }

  /** Enrollment record for local storage (DEMO ONLY; contains private key). */
  public static record Record(
      Integer enrollmentId,
      Integer integrationId,
      String enrollmentName,
      String enrollmentUrl,
      String integrationPublicKey,
      String enrollmentProofToken,
      String devicePublicKey,
      String devicePrivateKey,
      Boolean authAttemptChallengeRequired,
      String deviceLabel,
      String createdAt,
      // New integration information fields
      String integrationName,
      String integrationDescription,
      String integrationLogo) {}
}
