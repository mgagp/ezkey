/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: PublicInstanceInfoService
 * Description: Builds public instance metadata for Admin API and Auth API instance-info endpoints.
 */

package org.ezkey.instance.service;

import java.util.Locale;
import java.util.regex.Pattern;
import org.ezkey.config.OrganizationProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

/**
 * Builds {@link PublicInstanceInfoResponseDto} from {@link QrCodeProperties}, {@link
 * OrganizationProperties} and the build identity of the running process.
 *
 * <p>Build identity (#1477): {@code productVersion} comes from {@code
 * META-INF/build-info.properties} (Spring Boot {@link BuildProperties}); {@code gitShortSha} comes
 * from {@code ezkey.build.revision}, which the Docker image sets from its {@code
 * OCI_IMAGE_REVISION} build argument. Only a 7 to 64 character hex SHA (SHA-1 or SHA-256 object id)
 * is exposed, cut to 7 characters; anything else (unset, {@code unknown}) yields {@code null},
 * never an echo.
 */
@Service
public class PublicInstanceInfoService {

  /** Length of the short SHA, same as the Admin UI chrome and the deploy ledger. */
  static final int SHORT_SHA_LENGTH = 7;

  private static final Pattern GIT_SHA = Pattern.compile("[0-9a-f]{7,64}");

  private final QrCodeProperties qrCodeProperties;

  private final OrganizationProperties organizationProperties;

  private final String productVersion;

  private final String gitShortSha;

  /**
   * Creates the service.
   *
   * @param qrCodeProperties QR settings (public Auth API base URL)
   * @param organizationProperties instance branding
   * @param buildProperties build-info of this module, absent when the build did not generate it
   * @param buildRevision full git SHA of the deployed tree ({@code ezkey.build.revision})
   */
  public PublicInstanceInfoService(
      QrCodeProperties qrCodeProperties,
      OrganizationProperties organizationProperties,
      ObjectProvider<BuildProperties> buildProperties,
      @Value("${ezkey.build.revision:}") String buildRevision) {
    this.qrCodeProperties = qrCodeProperties;
    this.organizationProperties = organizationProperties;
    BuildProperties build = buildProperties.getIfAvailable();
    this.productVersion = build == null ? null : blankToNull(build.getVersion());
    this.gitShortSha = toShortSha(buildRevision);
  }

  /**
   * Returns public instance information (branding, optional public Auth API URL for QR alignment).
   *
   * @return instance metadata
   */
  public PublicInstanceInfoResponseDto getPublicInstanceInfo() {
    String authBase = qrCodeProperties.getAuthBaseUrl();
    if (authBase != null) {
      authBase = authBase.isBlank() ? null : authBase.strip();
    }

    String aboutUrl = organizationProperties.getAboutUrl();
    if (aboutUrl != null) {
      aboutUrl = aboutUrl.isBlank() ? null : aboutUrl.strip();
    }

    return new PublicInstanceInfoResponseDto(
        authBase,
        organizationProperties.getName(),
        organizationProperties.getDescription(),
        aboutUrl,
        productVersion,
        gitShortSha);
  }

  static String toShortSha(String revision) {
    if (revision == null) {
      return null;
    }
    String candidate = revision.strip().toLowerCase(Locale.ROOT);
    if (!GIT_SHA.matcher(candidate).matches()) {
      return null;
    }
    return candidate.substring(0, SHORT_SHA_LENGTH);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
