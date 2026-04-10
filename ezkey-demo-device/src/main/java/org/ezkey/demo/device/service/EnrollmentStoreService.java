package org.ezkey.demo.device.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

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
    ObjectMapper mapper = new ObjectMapper();
    mapper.writerWithDefaultPrettyPrinter();
    this.objectMapper = mapper;
    this.rootDir = Path.of("data", "enrollments");
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
                record.integrationLogo(),
                record.tenantId(),
                record.tenantName(),
                record.tenantDescription(),
                record.devicePrivateKeyStorageTier() != null
                    ? record.devicePrivateKeyStorageTier()
                    : "NONE");
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
      if (!Files.exists(file)) {
        return Optional.empty();
      }
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
      if (!Files.exists(rootDir)) {
        return items;
      }
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

  /**
   * Enrollment record for local storage (DEMO ONLY; contains private key). Per-parameter
   * documentation is on the all-args constructor, not on this type (Checkstyle JavadocType).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonAutoDetect(
      fieldVisibility = JsonAutoDetect.Visibility.ANY,
      getterVisibility = JsonAutoDetect.Visibility.NONE,
      isGetterVisibility = JsonAutoDetect.Visibility.NONE)
  public static final class Record {
    private Integer enrollmentId;
    private Integer integrationId;
    private String enrollmentName;
    private String enrollmentUrl;
    private String integrationPublicKey;
    private String enrollmentProofToken;
    private String devicePublicKey;
    private String devicePrivateKey;
    private Boolean authAttemptChallengeRequired;
    private String deviceLabel;
    private String createdAt;
    private String integrationName;
    private String integrationDescription;
    private String integrationLogo;
    private Integer tenantId;
    private String tenantName;
    private String tenantDescription;

    /** Defaults to NONE when absent from legacy JSON files. */
    private String devicePrivateKeyStorageTier = "NONE";

    /** Empty constructor for Jackson deserialization. */
    public Record() {}

    /**
     * Full enrollment snapshot for persistence.
     *
     * @param enrollmentId enrollment ID
     * @param integrationId integration ID
     * @param enrollmentName enrollment name
     * @param enrollmentUrl enrollment URL
     * @param integrationPublicKey integration public key
     * @param enrollmentProofToken enrollment proof token
     * @param devicePublicKey device public key
     * @param devicePrivateKey device private key
     * @param authAttemptChallengeRequired whether challenge is required
     * @param deviceLabel device label
     * @param createdAt creation timestamp
     * @param integrationName integration name
     * @param integrationDescription integration description
     * @param integrationLogo integration logo
     * @param tenantId tenant id
     * @param tenantName tenant name
     * @param tenantDescription tenant description
     * @param devicePrivateKeyStorageTier client-reported tier (demo device always NONE)
     */
    public Record(
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
        String integrationName,
        String integrationDescription,
        String integrationLogo,
        Integer tenantId,
        String tenantName,
        String tenantDescription,
        String devicePrivateKeyStorageTier) {
      this.enrollmentId = enrollmentId;
      this.integrationId = integrationId;
      this.enrollmentName = enrollmentName;
      this.enrollmentUrl = enrollmentUrl;
      this.integrationPublicKey = integrationPublicKey;
      this.enrollmentProofToken = enrollmentProofToken;
      this.devicePublicKey = devicePublicKey;
      this.devicePrivateKey = devicePrivateKey;
      this.authAttemptChallengeRequired = authAttemptChallengeRequired;
      this.deviceLabel = deviceLabel;
      this.createdAt = createdAt;
      this.integrationName = integrationName;
      this.integrationDescription = integrationDescription;
      this.integrationLogo = integrationLogo;
      this.tenantId = tenantId;
      this.tenantName = tenantName;
      this.tenantDescription = tenantDescription;
      this.devicePrivateKeyStorageTier =
          devicePrivateKeyStorageTier != null ? devicePrivateKeyStorageTier : "NONE";
    }

    public Integer enrollmentId() {
      return enrollmentId;
    }

    public Integer integrationId() {
      return integrationId;
    }

    public String enrollmentName() {
      return enrollmentName;
    }

    public String enrollmentUrl() {
      return enrollmentUrl;
    }

    public String integrationPublicKey() {
      return integrationPublicKey;
    }

    public String enrollmentProofToken() {
      return enrollmentProofToken;
    }

    public String devicePublicKey() {
      return devicePublicKey;
    }

    public String devicePrivateKey() {
      return devicePrivateKey;
    }

    public Boolean authAttemptChallengeRequired() {
      return authAttemptChallengeRequired;
    }

    public String deviceLabel() {
      return deviceLabel;
    }

    public String createdAt() {
      return createdAt;
    }

    public String integrationName() {
      return integrationName;
    }

    public String integrationDescription() {
      return integrationDescription;
    }

    public String integrationLogo() {
      return integrationLogo;
    }

    public Integer tenantId() {
      return tenantId;
    }

    public String tenantName() {
      return tenantName;
    }

    public String tenantDescription() {
      return tenantDescription;
    }

    public String devicePrivateKeyStorageTier() {
      return devicePrivateKeyStorageTier != null ? devicePrivateKeyStorageTier : "NONE";
    }

    public void setEnrollmentId(Integer enrollmentId) {
      this.enrollmentId = enrollmentId;
    }

    public void setIntegrationId(Integer integrationId) {
      this.integrationId = integrationId;
    }

    public void setEnrollmentName(String enrollmentName) {
      this.enrollmentName = enrollmentName;
    }

    public void setEnrollmentUrl(String enrollmentUrl) {
      this.enrollmentUrl = enrollmentUrl;
    }

    public void setIntegrationPublicKey(String integrationPublicKey) {
      this.integrationPublicKey = integrationPublicKey;
    }

    public void setEnrollmentProofToken(String enrollmentProofToken) {
      this.enrollmentProofToken = enrollmentProofToken;
    }

    public void setDevicePublicKey(String devicePublicKey) {
      this.devicePublicKey = devicePublicKey;
    }

    public void setDevicePrivateKey(String devicePrivateKey) {
      this.devicePrivateKey = devicePrivateKey;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
      this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

    public void setDeviceLabel(String deviceLabel) {
      this.deviceLabel = deviceLabel;
    }

    public void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }

    public void setIntegrationName(String integrationName) {
      this.integrationName = integrationName;
    }

    public void setIntegrationDescription(String integrationDescription) {
      this.integrationDescription = integrationDescription;
    }

    public void setIntegrationLogo(String integrationLogo) {
      this.integrationLogo = integrationLogo;
    }

    public void setTenantId(Integer tenantId) {
      this.tenantId = tenantId;
    }

    public void setTenantName(String tenantName) {
      this.tenantName = tenantName;
    }

    public void setTenantDescription(String tenantDescription) {
      this.tenantDescription = tenantDescription;
    }

    public void setDevicePrivateKeyStorageTier(String devicePrivateKeyStorageTier) {
      this.devicePrivateKeyStorageTier =
          devicePrivateKeyStorageTier != null ? devicePrivateKeyStorageTier : "NONE";
    }
  }
}
