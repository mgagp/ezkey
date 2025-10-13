/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: AdminToken
 * Description: JPA entity representing a bearer token for administrator authentication.
 */

package org.ezkey.integration.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA entity representing a bearer token for administrator authentication.
 *
 * <p>This entity stores bearer tokens used for API authentication by administrators. Each token is
 * associated with a specific administrator and contains metadata about the authentication session
 * including IP address, user agent, and expiration.
 *
 * <p><b>Token Security:</b>
 *
 * <ul>
 *   <li><b>Unique Tokens:</b> Each token is unique and cannot be duplicated
 *   <li><b>Expiration:</b> Tokens have configurable expiration times
 *   <li><b>Audit Trail:</b> All token usage is tracked for security
 *   <li><b>Revocation:</b> Tokens can be invalidated for security purposes
 * </ul>
 *
 * <p><b>Token Metadata:</b>
 *
 * <ul>
 *   <li><b>IP Address:</b> Tracks the IP address from which the token was issued
 *   <li><b>User Agent:</b> Records the user agent string for audit purposes
 *   <li><b>Last Used:</b> Tracks when the token was last used for authentication
 *   <li><b>Expiration:</b> Configurable expiration time for token validity
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EzkeyAdmin
 * @see Tenant
 * @see Integration
 */
@Entity
@Table(name = "ezkey_admin_tokens")
public class AdminToken {

  /**
   * Primary key identifier for the token.
   *
   * <p>This field is auto-generated using the database identity column.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "token_id")
  private Integer tokenId;

  /**
   * The bearer token string used for authentication.
   *
   * <p>This field contains the actual token string that is sent in the Authorization header for API
   * authentication.
   */
  @Column(name = "bearer_token", nullable = false, length = 255, unique = true)
  private String bearerToken;

  /**
   * Reference to the administrator who owns this token.
   *
   * <p>This field establishes the relationship between the token and the administrator who can use
   * it for authentication.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "admin_id", nullable = false)
  private EzkeyAdmin admin;

  /**
   * Type of administrator associated with this token.
   *
   * <p>This field stores the administrator type for quick access without needing to join with the
   * admin table.
   */
  @Column(name = "admin_type", nullable = false, length = 20)
  private String adminType;

  /**
   * Reference to the tenant associated with this token.
   *
   * <p>This field is null for global administrators and contains the tenant reference for tenant
   * and integration administrators.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  private Tenant tenant;

  /**
   * Reference to the integration associated with this token.
   *
   * <p>This field is only used for integration administrators and specifies which integration they
   * can manage.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "integration_id")
  private Integration integration;

  /**
   * Timestamp when the token was created.
   *
   * <p>This field is automatically set to the current timestamp when the token is created.
   */
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /**
   * Timestamp when the token expires.
   *
   * <p>This field determines when the token becomes invalid and can no longer be used for
   * authentication.
   */
  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  /**
   * Timestamp of the last time this token was used.
   *
   * <p>This field is updated each time the token is used for authentication to track usage
   * patterns.
   */
  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  /**
   * IP address from which the token was issued.
   *
   * <p>This field stores the IP address for security audit and can be used to detect suspicious
   * activity.
   */
  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  /**
   * User agent string from which the token was issued.
   *
   * <p>This field stores the user agent for security audit and can be used to detect suspicious
   * activity.
   */
  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  /**
   * Flag indicating whether the token is active.
   *
   * <p>Inactive tokens cannot be used for authentication but are preserved for audit purposes.
   */
  @Column(name = "active", nullable = false)
  private Boolean active = true;

  /**
   * Default constructor for JPA.
   *
   * <p>This constructor is required by JPA and should not be used for business logic. Use the
   * parameterized constructor instead.
   */
  public AdminToken() {
    // Default constructor for JPA
  }

  /**
   * Constructs a new admin token with the specified details.
   *
   * <p>This constructor creates a new admin token with the provided bearer token, administrator,
   * and expiration time.
   *
   * @param bearerToken the bearer token string
   * @param admin the administrator who owns this token
   * @param adminType the type of administrator
   * @param expiresAt the expiration timestamp
   */
  public AdminToken(
      String bearerToken, EzkeyAdmin admin, String adminType, LocalDateTime expiresAt) {
    this.bearerToken = bearerToken;
    this.admin = admin;
    this.adminType = adminType;
    this.expiresAt = expiresAt;
    this.createdAt = LocalDateTime.now();
    this.active = true;
  }

  /**
   * Gets the token ID.
   *
   * @return the token ID
   */
  public Integer getTokenId() {
    return tokenId;
  }

  /**
   * Sets the token ID.
   *
   * @param tokenId the token ID
   */
  public void setTokenId(Integer tokenId) {
    this.tokenId = tokenId;
  }

  /**
   * Gets the bearer token.
   *
   * @return the bearer token
   */
  public String getBearerToken() {
    return bearerToken;
  }

  /**
   * Sets the bearer token.
   *
   * @param bearerToken the bearer token
   */
  public void setBearerToken(String bearerToken) {
    this.bearerToken = bearerToken;
  }

  /**
   * Gets the administrator.
   *
   * @return the administrator
   */
  public EzkeyAdmin getAdmin() {
    return admin;
  }

  /**
   * Sets the administrator.
   *
   * @param admin the administrator
   */
  public void setAdmin(EzkeyAdmin admin) {
    this.admin = admin;
  }

  /**
   * Gets the administrator type.
   *
   * @return the administrator type
   */
  public String getAdminType() {
    return adminType;
  }

  /**
   * Sets the administrator type.
   *
   * @param adminType the administrator type
   */
  public void setAdminType(String adminType) {
    this.adminType = adminType;
  }

  /**
   * Gets the tenant.
   *
   * @return the tenant
   */
  public Tenant getTenant() {
    return tenant;
  }

  /**
   * Sets the tenant.
   *
   * @param tenant the tenant
   */
  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  /**
   * Gets the integration.
   *
   * @return the integration
   */
  public Integration getIntegration() {
    return integration;
  }

  /**
   * Sets the integration.
   *
   * @param integration the integration
   */
  public void setIntegration(Integration integration) {
    this.integration = integration;
  }

  /**
   * Gets the creation timestamp.
   *
   * @return the creation timestamp
   */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp.
   *
   * @param createdAt the creation timestamp
   */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the expiration timestamp.
   *
   * @return the expiration timestamp
   */
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the expiration timestamp.
   *
   * @param expiresAt the expiration timestamp
   */
  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  /**
   * Gets the last used timestamp.
   *
   * @return the last used timestamp
   */
  public LocalDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  /**
   * Sets the last used timestamp.
   *
   * @param lastUsedAt the last used timestamp
   */
  public void setLastUsedAt(LocalDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  /**
   * Gets the IP address.
   *
   * @return the IP address
   */
  public String getIpAddress() {
    return ipAddress;
  }

  /**
   * Sets the IP address.
   *
   * @param ipAddress the IP address
   */
  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  /**
   * Gets the user agent.
   *
   * @return the user agent
   */
  public String getUserAgent() {
    return userAgent;
  }

  /**
   * Sets the user agent.
   *
   * @param userAgent the user agent
   */
  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  /**
   * Gets the active status.
   *
   * @return true if the token is active
   */
  public Boolean getActive() {
    return active;
  }

  /**
   * Sets the active status.
   *
   * @param active the active status
   */
  public void setActive(Boolean active) {
    this.active = active;
  }

  /**
   * Returns a string representation of the admin token.
   *
   * @return string representation of the admin token
   */
  @Override
  public String toString() {
    return "AdminToken{"
        + "tokenId="
        + tokenId
        + ", bearerToken='"
        + bearerToken
        + '\''
        + ", adminType='"
        + adminType
        + '\''
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + ", active="
        + active
        + '}';
  }
}
