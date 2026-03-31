/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
   * Optimistic locking version for concurrent update protection.
   *
   * <p>Used by JPA to detect concurrent modifications. When a PATCH/PUT request includes a stale
   * version, the update fails and the API returns 409 Conflict. Client must re-fetch and retry.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L;

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
  private OffsetDateTime createdAt;

  /**
   * Flag indicating whether the tenant is active.
   *
   * <p>Inactive tenants cannot be used for new operations but existing data is preserved for audit
   * purposes.
   */
  @Column(name = "active", nullable = false)
  private Boolean active = true;

  /**
   * Flag indicating whether this is the system tenant.
   *
   * <p>System tenant hosts global administrators and represents the organization hosting this Ezkey
   * instance. Only one tenant should have this flag set to true. Application tenants (created by
   * global administrators) should have this flag set to false.
   *
   * <p>This flag provides a robust way to distinguish system tenant from application tenants
   * without relying on string comparisons or tenant IDs.
   */
  @Column(name = "is_system_tenant", nullable = false)
  private Boolean isSystemTenant = false;

  /**
   * Legal name of the organization.
   *
   * <p>Distinct from {@code tenantName} which is a short technical identifier. This field holds the
   * full legal entity name (e.g. "Acme Corporation Inc.").
   */
  @Column(name = "organization_name", length = 255)
  private String organizationName;

  /**
   * Primary domain of the organization (e.g. {@code acme.com}).
   *
   * <p>Used for email correlation, future SSO integration, and organizational identity
   * verification.
   */
  @Column(name = "organization_domain", length = 255)
  private String organizationDomain;

  /**
   * ISO 3166-1 alpha-2 country code for legal jurisdiction.
   *
   * <p>Determines data residency requirements and applicable compliance regulations (e.g. GDPR, SOC
   * 2).
   */
  @Column(name = "country_code", length = 2)
  private String countryCode;

  /**
   * IANA timezone identifier (e.g. {@code America/Montreal}).
   *
   * <p>Used for audit report timestamps, notification scheduling, and maintenance window
   * calculations.
   */
  @Column(name = "timezone", length = 50)
  private String timezone;

  /**
   * Name of the primary technical contact for this tenant.
   *
   * <p>SOC 2 CC2.1 requires an identifiable point of contact for security communications and
   * incident response.
   */
  @Column(name = "primary_contact_name", length = 255)
  private String primaryContactName;

  /**
   * Email of the primary technical contact.
   *
   * <p>Used for incident notifications, certificate expiration alerts, and operational
   * communications.
   */
  @Column(name = "primary_contact_email", length = 255)
  private String primaryContactEmail;

  /**
   * Timestamp of the last modification to this tenant record.
   *
   * <p>SOC 2 CC7.2 requires change tracking. This field is set automatically by the service layer
   * on every update.
   */
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  /**
   * Administrator who last modified this tenant record.
   *
   * <p>Provides accountability for changes (SOC 2 CC7.2).
   */
  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "updated_by_admin_id")
  private EzkeyAdmin updatedByAdmin;

  /**
   * Timestamp when the tenant was deactivated.
   *
   * <p>Records the exact moment of deactivation for audit trail (SOC 2 CC6.3 — access removal
   * tracking).
   */
  @Column(name = "deactivated_at")
  private OffsetDateTime deactivatedAt;

  /**
   * Administrator who deactivated this tenant.
   *
   * <p>Provides accountability for deactivation (SOC 2 CC6.3).
   */
  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "deactivated_by_admin_id")
  private EzkeyAdmin deactivatedByAdmin;

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
    this.createdAt = OffsetDateTime.now();
    this.active = true;
  }

  /**
   * Constructs a new tenant with identity and contact information.
   *
   * <p>This constructor creates a tenant with organizational identity fields in addition to the
   * basic name and description.
   *
   * @param tenantName the unique name of the tenant
   * @param tenantDescription the description (can be null)
   * @param organizationName the legal organization name (can be null)
   * @param primaryContactEmail the contact email (can be null)
   */
  public Tenant(
      String tenantName,
      String tenantDescription,
      String organizationName,
      String primaryContactEmail) {
    this(tenantName, tenantDescription);
    this.organizationName = organizationName;
    this.primaryContactEmail = primaryContactEmail;
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
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp.
   *
   * @param createdAt the creation timestamp
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
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
   * Gets the system tenant flag.
   *
   * @return true if this is the system tenant
   */
  public Boolean getIsSystemTenant() {
    return isSystemTenant;
  }

  /**
   * Sets the system tenant flag.
   *
   * @param isSystemTenant true if this is the system tenant
   */
  public void setIsSystemTenant(Boolean isSystemTenant) {
    this.isSystemTenant = isSystemTenant;
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
   * Gets the legal organization name.
   *
   * @return the organization name
   */
  public String getOrganizationName() {
    return organizationName;
  }

  /**
   * Sets the legal organization name.
   *
   * @param organizationName the organization name
   */
  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
  }

  /**
   * Gets the organization domain.
   *
   * @return the organization domain
   */
  public String getOrganizationDomain() {
    return organizationDomain;
  }

  /**
   * Sets the organization domain.
   *
   * @param organizationDomain the organization domain
   */
  public void setOrganizationDomain(String organizationDomain) {
    this.organizationDomain = organizationDomain;
  }

  /**
   * Gets the ISO 3166-1 alpha-2 country code.
   *
   * @return the country code
   */
  public String getCountryCode() {
    return countryCode;
  }

  /**
   * Sets the ISO 3166-1 alpha-2 country code.
   *
   * @param countryCode the country code (2 uppercase letters)
   */
  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  /**
   * Gets the IANA timezone identifier.
   *
   * @return the timezone
   */
  public String getTimezone() {
    return timezone;
  }

  /**
   * Sets the IANA timezone identifier.
   *
   * @param timezone the timezone (e.g. America/Montreal)
   */
  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  /**
   * Gets the primary contact name.
   *
   * @return the primary contact name
   */
  public String getPrimaryContactName() {
    return primaryContactName;
  }

  /**
   * Sets the primary contact name.
   *
   * @param primaryContactName the primary contact name
   */
  public void setPrimaryContactName(String primaryContactName) {
    this.primaryContactName = primaryContactName;
  }

  /**
   * Gets the primary contact email.
   *
   * @return the primary contact email
   */
  public String getPrimaryContactEmail() {
    return primaryContactEmail;
  }

  /**
   * Sets the primary contact email.
   *
   * @param primaryContactEmail the primary contact email
   */
  public void setPrimaryContactEmail(String primaryContactEmail) {
    this.primaryContactEmail = primaryContactEmail;
  }

  /**
   * Gets the last update timestamp.
   *
   * @return the update timestamp
   */
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Sets the last update timestamp.
   *
   * @param updatedAt the update timestamp
   */
  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  /**
   * Gets the admin who last updated this tenant.
   *
   * @return the updating administrator
   */
  public EzkeyAdmin getUpdatedByAdmin() {
    return updatedByAdmin;
  }

  /**
   * Sets the admin who last updated this tenant.
   *
   * @param updatedByAdmin the updating administrator
   */
  public void setUpdatedByAdmin(EzkeyAdmin updatedByAdmin) {
    this.updatedByAdmin = updatedByAdmin;
  }

  /**
   * Gets the deactivation timestamp.
   *
   * @return the deactivation timestamp
   */
  public OffsetDateTime getDeactivatedAt() {
    return deactivatedAt;
  }

  /**
   * Sets the deactivation timestamp.
   *
   * @param deactivatedAt the deactivation timestamp
   */
  public void setDeactivatedAt(OffsetDateTime deactivatedAt) {
    this.deactivatedAt = deactivatedAt;
  }

  /**
   * Gets the admin who deactivated this tenant.
   *
   * @return the deactivating administrator
   */
  public EzkeyAdmin getDeactivatedByAdmin() {
    return deactivatedByAdmin;
  }

  /**
   * Sets the admin who deactivated this tenant.
   *
   * @param deactivatedByAdmin the deactivating administrator
   */
  public void setDeactivatedByAdmin(EzkeyAdmin deactivatedByAdmin) {
    this.deactivatedByAdmin = deactivatedByAdmin;
  }

  /**
   * Gets the optimistic lock version.
   *
   * @return the version
   */
  public Long getVersion() {
    return version;
  }

  /**
   * Sets the optimistic lock version (used by JPA; do not set manually).
   *
   * @param version the version
   */
  public void setVersion(Long version) {
    this.version = version;
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
        + ", organizationName='"
        + organizationName
        + '\''
        + ", countryCode='"
        + countryCode
        + '\''
        + ", createdAt="
        + createdAt
        + ", active="
        + active
        + '}';
  }
}
