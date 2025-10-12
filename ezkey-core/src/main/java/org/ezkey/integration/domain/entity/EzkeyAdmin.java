/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EzkeyAdmin
 * Description: JPA entity representing an administrator in the Ezkey system.
 */

package org.ezkey.integration.domain.entity;

import java.time.OffsetDateTime;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.AdminTempToken;
import org.ezkey.enrollment.domain.entity.Enrollment;

/**
 * JPA entity representing an administrator in the Ezkey system.
 * <p>
 * This entity represents different types of administrators in the multi-tenant
 * Ezkey system. Administrators have different levels of access based on their
 * type and scope of authority within the system.
 * </p>
 *
 * <p>
 * <b>Administrator Types:</b>
 * <ul>
 * <li><b>GLOBAL_ADMIN:</b> System-wide administrator with full access</li>
 * <li><b>TENANT_ADMIN:</b> Tenant-specific administrator with limited scope</li>
 * <li><b>INTEGRATION_ADMIN:</b> Integration-specific administrator with minimal scope</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Features:</b>
 * <ul>
 * <li><b>Password Hashing:</b> Passwords are stored as BCrypt hashes</li>
 * <li><b>MFA Support:</b> Multi-factor authentication using Ezkey enrollment</li>
 * <li><b>Token Management:</b> Bearer tokens for API authentication</li>
 * <li><b>Audit Trail:</b> Login tracking and password change history</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Tenant
 * @see Integration
 * @see Enrollment
 */
@Entity
@Table(name = "ezkey_admin")
public class EzkeyAdmin {

    /**
     * Enumeration of administrator types in the Ezkey system.
     * <p>
     * Each type has different permissions and scope of authority.
     * </p>
     */
    public enum AdminType {
        /**
         * Global administrator with system-wide access.
         * Can create tenants and manage all system resources.
         */
        GLOBAL_ADMIN,
        
        /**
         * Tenant administrator with access to a specific tenant.
         * Can manage integrations and users within their tenant.
         */
        TENANT_ADMIN,
        
        /**
         * Integration administrator with access to a specific integration.
         * Can manage enrollments and auth attempts for their integration.
         */
        INTEGRATION_ADMIN
    }

    /**
     * Primary key identifier for the administrator.
     * <p>
     * This field is auto-generated using the database identity column.
     * </p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Integer adminId;

    /**
     * Unique username for the administrator.
     * <p>
     * This field is required and must be unique across all administrators.
     * Used for authentication and identification purposes.
     * </p>
     */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /**
     * BCrypt hashed password for the administrator.
     * <p>
     * This field stores the password hash using BCrypt algorithm
     * with appropriate strength for security.
     * </p>
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Type of administrator determining their permissions.
     * <p>
     * This field determines the scope of authority and permissions
     * available to this administrator.
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_type", nullable = false, length = 20)
    private AdminType adminType;

    /**
     * Reference to the tenant this administrator belongs to.
     * <p>
     * This field is null for global administrators and required
     * for tenant and integration administrators.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /**
     * Reference to the integration this administrator manages.
     * <p>
     * This field is only used for integration administrators
     * and specifies which integration they can manage.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private Integration integration;

    /**
     * Flag indicating whether MFA is enabled for this administrator.
     * <p>
     * When enabled, the administrator must use MFA for authentication.
     * </p>
     */
    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = true;

    /**
     * Flag indicating whether MFA is required for this administrator.
     * <p>
     * When required, the administrator cannot disable MFA and must
     * use it for all authentication attempts.
     * </p>
     */
    @Column(name = "mfa_required", nullable = false)
    private Boolean mfaRequired = true;

    /**
     * Reference to the MFA enrollment for this administrator.
     * <p>
     * This field links to the enrollment record used for MFA
     * authentication using the Ezkey system.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mfa_enrollment_id")
    private Enrollment mfaEnrollment;

    /**
     * Flag indicating whether password change is required.
     * <p>
     * When true, the administrator must change their password
     * on next login for security purposes.
     * </p>
     */
    @Column(name = "password_change_required", nullable = false)
    private Boolean passwordChangeRequired = false;

    /**
     * Reference to the administrator who created this account.
     * <p>
     * This field tracks the administrator responsible for creating
     * this administrator account.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private EzkeyAdmin createdByAdmin;

    /**
     * Timestamp when the administrator account was created.
     * <p>
     * This field is automatically set to the current timestamp
     * when the administrator is created.
     * </p>
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Timestamp of the last login for this administrator.
     * <p>
     * This field is updated each time the administrator successfully
     * authenticates to the system.
     * </p>
     */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /**
     * Timestamp of the last password change for this administrator.
     * <p>
     * This field is updated each time the administrator changes
     * their password for audit purposes.
     * </p>
     */
    @Column(name = "last_password_change")
    private OffsetDateTime lastPasswordChange;

    /**
     * Flag indicating whether the administrator account is active.
     * <p>
     * Inactive administrators cannot authenticate to the system
     * but their data is preserved for audit purposes.
     * </p>
     */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * List of bearer tokens for this administrator.
     * <p>
     * This relationship includes all active and expired tokens
     * for this administrator for audit and security purposes.
     * </p>
     */
    @OneToMany(mappedBy = "admin")
    private List<AdminToken> tokens;

    /**
     * List of temporary tokens for this administrator.
     * <p>
     * This relationship includes all temporary tokens used
     * for MFA authentication flows.
     * </p>
     */
    @OneToMany(mappedBy = "admin")
    private List<AdminTempToken> tempTokens;

    /**
     * Default constructor for JPA.
     * <p>
     * This constructor is required by JPA and should not be used
     * for business logic. Use the parameterized constructor instead.
     * </p>
     */
    public EzkeyAdmin() {
        // Default constructor for JPA
    }

    /**
     * Constructs a new administrator with the specified details.
     * <p>
     * This constructor creates a new administrator with the provided
     * username, password hash, and type. The created timestamp is set
     * to the current time and the administrator is marked as active.
     * </p>
     *
     * @param username the unique username for the administrator
     * @param passwordHash the BCrypt hashed password
     * @param adminType the type of administrator
     */
    public EzkeyAdmin(String username, String passwordHash, AdminType adminType) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.adminType = adminType;
        this.createdAt = OffsetDateTime.now();
        this.active = true;
        this.mfaEnabled = true;
        this.mfaRequired = true;
        this.passwordChangeRequired = false;
    }

    /**
     * Gets the administrator ID.
     *
     * @return the administrator ID
     */
    public Integer getAdminId() {
        return adminId;
    }

    /**
     * Sets the administrator ID.
     *
     * @param adminId the administrator ID
     */
    public void setAdminId(Integer adminId) {
        this.adminId = adminId;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password hash.
     *
     * @return the password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the password hash.
     *
     * @param passwordHash the password hash
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Gets the administrator type.
     *
     * @return the administrator type
     */
    public AdminType getAdminType() {
        return adminType;
    }

    /**
     * Sets the administrator type.
     *
     * @param adminType the administrator type
     */
    public void setAdminType(AdminType adminType) {
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
     * Gets the MFA enabled status.
     *
     * @return true if MFA is enabled
     */
    public Boolean getMfaEnabled() {
        return mfaEnabled;
    }

    /**
     * Sets the MFA enabled status.
     *
     * @param mfaEnabled the MFA enabled status
     */
    public void setMfaEnabled(Boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    /**
     * Gets the MFA required status.
     *
     * @return true if MFA is required
     */
    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    /**
     * Sets the MFA required status.
     *
     * @param mfaRequired the MFA required status
     */
    public void setMfaRequired(Boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    /**
     * Gets the MFA enrollment.
     *
     * @return the MFA enrollment
     */
    public Enrollment getMfaEnrollment() {
        return mfaEnrollment;
    }

    /**
     * Sets the MFA enrollment.
     *
     * @param mfaEnrollment the MFA enrollment
     */
    public void setMfaEnrollment(Enrollment mfaEnrollment) {
        this.mfaEnrollment = mfaEnrollment;
    }

    /**
     * Gets the password change required status.
     *
     * @return true if password change is required
     */
    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /**
     * Sets the password change required status.
     *
     * @param passwordChangeRequired the password change required status
     */
    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Gets the creating administrator.
     *
     * @return the creating administrator
     */
    public EzkeyAdmin getCreatedByAdmin() {
        return createdByAdmin;
    }

    /**
     * Sets the creating administrator.
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
     * Gets the last login timestamp.
     *
     * @return the last login timestamp
     */
    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * Sets the last login timestamp.
     *
     * @param lastLoginAt the last login timestamp
     */
    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * Gets the last password change timestamp.
     *
     * @return the last password change timestamp
     */
    public OffsetDateTime getLastPasswordChange() {
        return lastPasswordChange;
    }

    /**
     * Sets the last password change timestamp.
     *
     * @param lastPasswordChange the last password change timestamp
     */
    public void setLastPasswordChange(OffsetDateTime lastPasswordChange) {
        this.lastPasswordChange = lastPasswordChange;
    }

    /**
     * Gets the active status.
     *
     * @return true if the administrator is active
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
     * Gets the list of tokens for this administrator.
     *
     * @return the list of tokens
     */
    public List<AdminToken> getTokens() {
        return tokens;
    }

    /**
     * Sets the list of tokens for this administrator.
     *
     * @param tokens the list of tokens
     */
    public void setTokens(List<AdminToken> tokens) {
        this.tokens = tokens;
    }

    /**
     * Gets the list of temporary tokens for this administrator.
     *
     * @return the list of temporary tokens
     */
    public List<AdminTempToken> getTempTokens() {
        return tempTokens;
    }

    /**
     * Sets the list of temporary tokens for this administrator.
     *
     * @param tempTokens the list of temporary tokens
     */
    public void setTempTokens(List<AdminTempToken> tempTokens) {
        this.tempTokens = tempTokens;
    }

    /**
     * Returns a string representation of the administrator.
     *
     * @return string representation of the administrator
     */
    @Override
    public String toString() {
        return "EzkeyAdmin{" +
                "adminId=" + adminId +
                ", username='" + username + '\'' +
                ", adminType=" + adminType +
                ", mfaEnabled=" + mfaEnabled +
                ", mfaRequired=" + mfaRequired +
                ", active=" + active +
                '}';
    }
}
