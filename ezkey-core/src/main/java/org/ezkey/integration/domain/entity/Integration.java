/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EzkeyIntegration
 * Description: JPA entity representing an integration in the Ezkey system.
 */

package org.ezkey.integration.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * JPA entity representing an integration in the Ezkey system.
 *
 * <p>
 * An integration represents an application or system that is protected by Ezkey
 * MFA. Each
 * integration can have multiple internationalization entries (i18n) for
 * different languages.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * <p>
 * <b>Table:</b> ezkey_integration
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_integration")
public class Integration {

    /**
     * Unique identifier for the integration. Auto-generated using database
     * identity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_id")
    private Integer id;

    /**
     * URL or path to the integration's logo image. Displayed in the mobile app and
     * web interfaces.
     */
    @Column(name = "integration_logo")
    private String logo;

    /**
     * Flag indicating whether the integration is active and available for use.
     * Inactive integrations
     * cannot be used for authentication. Defaults to {@code true} (matches DB:
     * {@code DEFAULT TRUE}).
     */
    @Column(name = "integration_active", nullable = false)
    private Boolean active = true;

    /**
     * Timestamp when the integration was created. Automatically set when the entity
     * is persisted.
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Collection of internationalization entries for this integration. Each entry
     * contains localized
     * name and description for different languages. Uses lazy loading for
     * performance optimization.
     *
     * <p>
     * <b>⚠ Concurrency hazard — CascadeType.ALL + orphanRemoval:</b> Because this
     * collection is
     * mapped with {@code cascade = CascadeType.ALL, orphanRemoval = true},
     * Hibernate tracks it as an
     * <em>owned</em> {@code PersistentBag}. Under concurrent load, if multiple
     * sessions independently
     * load the same {@code Integration} row (e.g., the shared system integration
     * used by all admin
     * MFA enrollments) and then flush within the same persistence context,
     * Hibernate may detect that
     * two entity states reference the same collection object and throw:
     *
     * <pre>
     * HibernateException: Found shared references to a collection:
     *     org.ezkey.integration.domain.entity.Integration.i18n
     * </pre>
     *
     * <p>
     * <b>Rule for callers:</b> Whenever you need to <em>read</em> this collection
     * (e.g., to
     * resolve localized names in the bind flow), always load the entity via {@link
     * org.ezkey.integration.domain.repository.IntegrationRepository#findByIdWithI18nAndTenant}
     * which
     * applies {@code @QueryHints(readOnly=true)} to suppress cascade tracking. Only
     * use {@code
     * findById()} when you intend to persist changes to the integration or its i18n
     * entries.
     *
     * <p>
     * See {@code agents.md} §"CascadeType.ALL + orphanRemoval shared-reference
     * hazard".
     */
    @OneToMany(mappedBy = "integration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IntegrationI18n> i18n;

    /**
     * Reference to the tenant this integration belongs to.
     *
     * <p>
     * This field is required for multi-tenant data isolation. Each integration
     * belongs to a
     * specific tenant and can only be accessed by administrators of that tenant.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /**
     * Flag indicating whether this is a system integration.
     *
     * <p>
     * System integrations are special integrations that are created by the system
     * itself (like
     * Ezkey admin interfaces) and are not managed by regular tenant administrators.
     */
    @Column(name = "is_system_integration")
    private Boolean isSystemIntegration = false;

    /**
     * Unique business identifier code for the integration within a tenant.
     *
     * <p>
     * This field must be unique per tenant and is used to reference the integration
     * by a
     * human-readable code (e.g., "web-portal", "mobile-app"). Follows slug format
     * (alphanumeric,
     * hyphens, underscores).
     */
    @Column(name = "integration_code", nullable = false)
    private String code;

    /**
     * Reference to the administrator who created this integration.
     *
     * <p>
     * This field tracks the administrator responsible for creating this integration
     * for audit and
     * permission purposes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private EzkeyAdmin createdByAdmin;

    /** Default constructor for JPA. */
    public Integration() {
        // active: field init. createdAt: @PrePersist when null. MapStruct ignores both
        // (create→entity).
    }

    /**
     * Sets {@code createdAt} when null before persist. MapStruct create→entity
     * ignores it; matches DB
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

    /**
     * Gets the URL or path to the integration's logo.
     *
     * @return the logo URL/path
     */
    public String getLogo() {
        return logo;
    }

    /**
     * Sets the URL or path to the integration's logo.
     *
     * @param logo the logo URL/path to set
     */
    public void setLogo(String logo) {
        this.logo = logo;
    }

    /**
     * Gets the active status of the integration.
     *
     * @return true if the integration is active, false otherwise
     */
    public Boolean getActive() {
        return active;
    }

    /**
     * Sets the active status of the integration.
     *
     * @param active the active status to set
     */
    public void setActive(Boolean active) {
        this.active = active;
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
     * Gets the collection of internationalization entries for this integration.
     *
     * @return the list of i18n entries
     */
    public List<IntegrationI18n> getI18n() {
        return i18n;
    }

    /**
     * Sets the collection of internationalization entries for this integration.
     *
     * @param i18n the list of i18n entries to set
     */
    public void setI18n(List<IntegrationI18n> i18n) {
        this.i18n = i18n;
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
