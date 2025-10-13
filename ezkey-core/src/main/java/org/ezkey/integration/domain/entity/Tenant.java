/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: Tenant
 * Description: JPA entity representing a tenant in the multi-tenant Ezkey system.
 */

package org.ezkey.integration.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA entity representing a tenant in the multi-tenant Ezkey system.
 *
 * <p>A tenant represents an organization or company that uses the Ezkey system. Each tenant has its
 * own isolated data and can have multiple administrators and integrations. Tenants are created by
 * global administrators and provide the foundation for multi-tenant data isolation.
 *
 * <p><b>Multi-Tenant Architecture:</b>
 *
 * <ul>
 *   <li><b>Data Isolation:</b> Each tenant has completely isolated data
 *   <li><b>Administrator Hierarchy:</b> Tenants can have tenant admins and integration admins
 *   <li><b>Integration Management:</b> Each tenant manages its own integrations
 *   <li><b>Security:</b> Tenant data is only accessible by authorized administrators
 * </ul>
 *
 * <p><b>Relationships:</b>
 *
 * <ul>
 *   <li><b>Created By:</b> Reference to the global administrator who created this tenant
 *   <li><b>Administrators:</b> List of administrators belonging to this tenant
 *   <li><b>Integrations:</b> List of integrations belonging to this tenant
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EzkeyAdmin
 * @see Integration
 */
@Entity
@Table(name = "ezkey_tenant")
public class Tenant {

  /**
   * Primary key identifier for the tenant.
   *
   * <p>This field is auto-generated using the database identity column.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "tenant_id")
  private Integer tenantId;

  /**
   * Unique name of the tenant organization.
   *
   * <p>This field is required and must be unique across all tenants. Used for identification and
   * display purposes.
   */
  @Column(name = "tenant_name", nullable = false, length = 100)
  private String tenantName;

  /**
   * Optional description of the tenant organization.
   *
   * <p>This field provides additional context about the tenant, such as company description or
   * purpose.
   */
  @Column(name = "tenant_description", columnDefinition = "TEXT")
  private String tenantDescription;

  /**
   * Reference to the global administrator who created this tenant.
   *
   * <p>This field tracks the administrator responsible for creating this tenant in the system.
   */
  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "created_by_admin_id")
  private EzkeyAdmin createdByAdmin;

  /**
   * Timestamp when the tenant was created.
   *
   * <p>This field is automatically set to the current timestamp when the tenant is created.
   */
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /**
   * Flag indicating whether the tenant is active.
   *
   * <p>Inactive tenants cannot be used for new operations but existing data is preserved for audit
   * purposes.
   */
  @Column(name = "active", nullable = false)
  private Boolean active = true;

  /**
   * List of administrators belonging to this tenant.
   *
   * <p>This relationship includes both tenant administrators and integration administrators for
   * this tenant.
   */
  @OneToMany(mappedBy = "tenant")
  private List<EzkeyAdmin> administrators;

  /**
   * List of integrations belonging to this tenant.
   *
   * <p>This relationship includes all integrations created within this tenant's scope.
   */
  @OneToMany(mappedBy = "tenant")
  private List<Integration> integrations;

  /**
   * Default constructor for JPA.
   *
   * <p>This constructor is required by JPA and should not be used for business logic. Use the
   * parameterized constructor instead.
   */
  public Tenant() {
    // Default constructor for JPA
  }

  /**
   * Constructs a new tenant with the specified name and description.
   *
   * <p>This constructor creates a new tenant with the provided name and description. The created
   * timestamp is set to the current time and the tenant is marked as active by default.
   *
   * @param tenantName the unique name of the tenant
   * @param tenantDescription the description of the tenant (can be null)
   */
  public Tenant(String tenantName, String tenantDescription) {
    this.tenantName = tenantName;
    this.tenantDescription = tenantDescription;
    this.createdAt = LocalDateTime.now();
    this.active = true;
  }

  /**
   * Gets the tenant ID.
   *
   * @return the tenant ID
   */
  public Integer getTenantId() {
    return tenantId;
  }

  /**
   * Sets the tenant ID.
   *
   * @param tenantId the tenant ID
   */
  public void setTenantId(Integer tenantId) {
    this.tenantId = tenantId;
  }

  /**
   * Gets the tenant name.
   *
   * @return the tenant name
   */
  public String getTenantName() {
    return tenantName;
  }

  /**
   * Sets the tenant name.
   *
   * @param tenantName the tenant name
   */
  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  /**
   * Gets the tenant description.
   *
   * @return the tenant description
   */
  public String getTenantDescription() {
    return tenantDescription;
  }

  /**
   * Sets the tenant description.
   *
   * @param tenantDescription the tenant description
   */
  public void setTenantDescription(String tenantDescription) {
    this.tenantDescription = tenantDescription;
  }

  /**
   * Gets the administrator who created this tenant.
   *
   * @return the creating administrator
   */
  public EzkeyAdmin getCreatedByAdmin() {
    return createdByAdmin;
  }

  /**
   * Sets the administrator who created this tenant.
   *
   * @param createdByAdmin the creating administrator
   */
  public void setCreatedByAdmin(EzkeyAdmin createdByAdmin) {
    this.createdByAdmin = createdByAdmin;
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
   * Gets the active status.
   *
   * @return true if the tenant is active
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
   * Gets the list of administrators for this tenant.
   *
   * @return the list of administrators
   */
  public List<EzkeyAdmin> getAdministrators() {
    return administrators;
  }

  /**
   * Sets the list of administrators for this tenant.
   *
   * @param administrators the list of administrators
   */
  public void setAdministrators(List<EzkeyAdmin> administrators) {
    this.administrators = administrators;
  }

  /**
   * Gets the list of integrations for this tenant.
   *
   * @return the list of integrations
   */
  public List<Integration> getIntegrations() {
    return integrations;
  }

  /**
   * Sets the list of integrations for this tenant.
   *
   * @param integrations the list of integrations
   */
  public void setIntegrations(List<Integration> integrations) {
    this.integrations = integrations;
  }

  /**
   * Returns a string representation of the tenant.
   *
   * @return string representation of the tenant
   */
  @Override
  public String toString() {
    return "Tenant{"
        + "tenantId="
        + tenantId
        + ", tenantName='"
        + tenantName
        + '\''
        + ", tenantDescription='"
        + tenantDescription
        + '\''
        + ", createdAt="
        + createdAt
        + ", active="
        + active
        + '}';
  }
}
