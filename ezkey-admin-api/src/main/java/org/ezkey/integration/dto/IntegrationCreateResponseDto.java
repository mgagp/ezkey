/**
 * Data Transfer Object (DTO) representing the response after successfully creating a new
 * Integration entity.
 *
 * <p>This DTO is returned by the API when a new integration is created via the admin interface. It
 * contains the unique identifier of the newly created integration, allowing clients to reference or
 * further interact with the integration resource.
 *
 * <p><b>Usage:</b> Used as the response body for POST operations on the integration resource.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * {
 *   "id": 42
 * }
 * </pre>
 *
 * @author Ezkey
 * @version 1.0
 * @since 2025
 * @see org.ezkey.integration.domain.IntegrationCreateResponse
 * @see org.ezkey.integration.mapper.IntegrationControllerMapper
 */
package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object (DTO) representing the response after successfully creating a new
 * Integration entity.
 *
 * <p>This DTO is returned by the API when a new integration is created via the admin interface. It
 * contains the unique identifier of the newly created integration, allowing clients to reference or
 * further interact with the integration resource.
 *
 * @author Ezkey
 * @version 1.0
 * @since 2025
 * @see org.ezkey.integration.domain.IntegrationCreateResponse
 * @see org.ezkey.integration.mapper.IntegrationControllerMapper
 */
@Schema(description = "Response DTO for creating new Integration entities")
public class IntegrationCreateResponseDto {

  /** The unique identifier of the newly created integration. */
  @Schema(description = "The unique identifier of the newly created integration", example = "42")
  private Integer id;

  /**
   * Returns the unique identifier of the newly created integration.
   *
   * @return the unique identifier of the newly created integration
   */
  public Integer getId() {
    return id;
  }

  /**
   * Sets the unique identifier of the newly created integration.
   *
   * @param id the unique identifier of the newly created integration
   */
  public void setId(Integer id) {
    this.id = id;
  }
}
