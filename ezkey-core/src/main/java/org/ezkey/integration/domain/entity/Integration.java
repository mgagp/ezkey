/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EzkeyIntegration
 * Description: JPA entity representing an integration in the Ezkey system.
 */

package org.ezkey.integration.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;

/**
 * JPA entity representing an integration in the Ezkey system.
 *
 * <p>An integration represents an application or system that is protected by Ezkey MFA. Each
 * integration has a single name and optional description.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Table:</b> ezkey_integration
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_integration")
public class Integration {

  /** Unique identifier for the integration. Auto-generated using database identity. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "integration_id")
  private Integer id;

  /** Explicit lifecycle state for the integration. */
  @Enumerated(EnumType.STRING)
  @Column(name = "integration_lifecycle_status", nullable = false)
  private IntegrationLifecycleStatus lifecycleStatus = IntegrationLifecycleStatus.ACTIVE;

  /** Timestamp when the integration was created. Automatically set when the entity is persisted. */
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /** Display name for the integration. Shown in admin interfaces and to users during enrollment. */
  @Column(name = "integration_name")
  private String name;

  /**
   * Optional description of the integration. Shown in admin interfaces and to users during
   * enrollment.
   */
  @Column(name = "integration_description")
  private String description;

  /**
   * Reference to the tenant this integration belongs to.
   *
   * <p>This field is required for multi-tenant data isolation. Each integration belongs to a
   * specific tenant and can only be accessed by administrators of that tenant.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  private Tenant tenant;

  /**
   * Flag indicating whether this is a system integration.
   *
   * <p>System integrations are special integrations that are created by the system itself (like
   * Ezkey admin interfaces) and are not managed by regular tenant administrators.
   */
  @Column(name = "is_system_integration")
  private Boolean isSystemIntegration = false;

  /**
   * Unique business identifier code for the integration within a tenant.
   *
   * <p>This field must be unique per tenant and is used to reference the integration by a
   * human-readable code (e.g., "web-portal", "mobile-app"). Follows slug format (alphanumeric,
   * hyphens, underscores).
   */
  @Column(name = "integration_code", nullable = false)
  private String code;

  /**
   * Reference to the administrator who created this integration.
   *
   * <p>This field tracks the administrator responsible for creating this integration for audit and
   * permission purposes.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_admin_id")
  private EzkeyAdmin createdByAdmin;

  /** Default constructor for JPA. */
  public Integration() {
    // lifecycleStatus: field init. createdAt: @PrePersist when null. MapStruct ignores both
    // (create→entity).
  }

  /**
   * Sets {@code createdAt} when null before persist. MapStruct create→entity ignores it; matches DB
   * default.
   */
  @PrePersist
  protected void prePersist() {
    if (this.createdAt == null) {
      this.createdAt = OffsetDateTime.now();
    }
  }

  /**
   * Gets the unique identifier of the integration.
   *
   * @return the integration ID
   */
  public Integer getId() {
    return id;
  }

  /**
   * Sets the unique identifier of the integration.
   *
   * @param id the integration ID to set
   */
  public void setId(Integer id) {
    this.id = id;
  }

  /** Returns the explicit lifecycle state. */
  public IntegrationLifecycleStatus getLifecycleStatus() {
    return lifecycleStatus;
  }

  /** Sets the explicit lifecycle state. */
  public void setLifecycleStatus(IntegrationLifecycleStatus lifecycleStatus) {
    this.lifecycleStatus =
        lifecycleStatus != null ? lifecycleStatus : IntegrationLifecycleStatus.ACTIVE;
  }

  /** Indicates whether the integration is in the ACTIVE lifecycle state. */
  public boolean isOperational() {
    return IntegrationLifecycleStatus.ACTIVE.equals(lifecycleStatus);
  }

  /** Indicates whether the integration is retired. */
  public boolean isRetired() {
    return IntegrationLifecycleStatus.RETIRED.equals(lifecycleStatus);
  }

  /**
   * Gets the creation timestamp of the integration.
   *
   * @return the creation timestamp
   */
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp of the integration.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the display name of the integration.
   *
   * @return the integration name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the display name of the integration.
   *
   * @param name the integration name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the optional description of the integration.
   *
   * @return the integration description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the optional description of the integration.
   *
   * @param description the integration description to set
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets the tenant this integration belongs to.
   *
   * @return the tenant
   */
  public Tenant getTenant() {
    return tenant;
  }

  /**
   * Sets the tenant this integration belongs to.
   *
   * @param tenant the tenant
   */
  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  /**
   * Gets the system integration flag.
   *
   * @return true if this is a system integration
   */
  public Boolean getIsSystemIntegration() {
    return isSystemIntegration;
  }

  /**
   * Sets the system integration flag.
   *
   * @param isSystemIntegration the system integration flag
   */
  public void setIsSystemIntegration(Boolean isSystemIntegration) {
    this.isSystemIntegration = isSystemIntegration;
  }

  /**
   * Gets the administrator who created this integration.
   *
   * @return the creating administrator
   */
  public EzkeyAdmin getCreatedByAdmin() {
    return createdByAdmin;
  }

  /**
   * Sets the administrator who created this integration.
   *
   * @param createdByAdmin the creating administrator
   */
  public void setCreatedByAdmin(EzkeyAdmin createdByAdmin) {
    this.createdByAdmin = createdByAdmin;
  }

  /**
   * Gets the unique business identifier code for the integration.
   *
   * @return the integration code
   */
  public String getCode() {
    return code;
  }

  /**
   * Sets the unique business identifier code for the integration.
   *
   * @param code the integration code to set
   */
  public void setCode(String code) {
    this.code = code;
  }
}
