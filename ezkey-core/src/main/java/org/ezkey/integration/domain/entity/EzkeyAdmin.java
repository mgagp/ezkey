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

import java.time.LocalDateTime;
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
 * <b>Security Features (Passwordless-Only):</b>
 * <ul>
 * <li><b>Passwordless Authentication:</b> Cryptographic authentication via Ezkey enrollment (FIDO2-like)</li>
 * <li><b>Recovery Codes:</b> Emergency access via single-use BCrypt-hashed recovery codes</li>
 * <li><b>Token Management:</b> Bearer tokens for API authentication</li>
 * <li><b>Audit Trail:</b> Login tracking and enrollment management</li>
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
     * Flag indicating whether challenge verification is required during passwordless auth.
     * <p>
     * When true, authentication attempts must include a 6-digit challenge code
     * verification during device approval for enhanced security.
     * </p>
     */
    @Column(name = "challenge_required", nullable = false)
    private Boolean challengeRequired = false;

    /**
     * Array of BCrypt hashed recovery codes for emergency access.
     * <p>
     * Recovery codes are single-use codes that allow an administrator to regain
     * access when their enrolled device is lost or unavailable. Each successful
     * recovery code use removes the code from the array (single-use enforcement).
     * </p>
     * <p>
     * <b>Format:</b> XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX (32 digits, 106-bit entropy)
     * <br>
     * <b>Count:</b> 10 codes per admin (generated during creation)
     * <br>
     * <b>Storage:</b> BCrypt hashed (paranoia-level security)
     * <br>
     * <b>Usage:</b> Single-use, grants 30-minute limited access token for enrollment reset
     * </p>
     */
    @Column(name = "recovery_codes")
    private String[] recoveryCodes;

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
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last login for this administrator.
     * <p>
     * This field is updated each time the administrator successfully
     * authenticates to the system.
     * </p>
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

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
     * This constructor creates a new passwordless administrator with the provided
     * username and type. The created timestamp is set to the current time and 
     * the administrator is marked as active. Passwordless is the only authentication
     * mode - no flag needed.
     * </p>
     *
     * @param username the unique username for the administrator
     * @param adminType the type of administrator
     */
    public EzkeyAdmin(String username, AdminType adminType) {
        this.username = username;
        this.adminType = adminType;
        this.createdAt = LocalDateTime.now();
        this.active = true;
        this.challengeRequired = false;
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
     * Gets the challenge required status.
     *
     * @return true if challenge verification is required during passwordless auth
     */
    public Boolean getChallengeRequired() {
        return challengeRequired;
    }

    /**
     * Sets the challenge required status.
     *
     * @param challengeRequired the challenge required status
     */
    public void setChallengeRequired(Boolean challengeRequired) {
        this.challengeRequired = challengeRequired;
    }

    /**
     * Gets the recovery codes array.
     *
     * @return array of BCrypt hashed recovery codes
     */
    public String[] getRecoveryCodes() {
        return recoveryCodes;
    }

    /**
     * Sets the recovery codes array.
     *
     * @param recoveryCodes array of BCrypt hashed recovery codes
     */
    public void setRecoveryCodes(String[] recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
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
     * Gets the last login timestamp.
     *
     * @return the last login timestamp
     */
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * Sets the last login timestamp.
     *
     * @param lastLoginAt the last login timestamp
     */
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
                ", challengeRequired=" + challengeRequired +
                ", active=" + active +
                '}';
    }
}
