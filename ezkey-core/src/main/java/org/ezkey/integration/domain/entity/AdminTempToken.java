/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: AdminTempToken
 * Description: JPA entity representing a temporary token for administrator MFA authentication.
 */

package org.ezkey.integration.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity representing a temporary token for administrator MFA authentication.
 * <p>
 * This entity stores temporary tokens used during the MFA authentication flow
 * for administrators. These tokens are short-lived and are used to bridge the
 * gap between password authentication and MFA verification.
 * </p>
 *
 * <p>
 * <b>MFA Flow:</b>
 * <ul>
 * <li><b>Password Auth:</b> Administrator provides username and password</li>
 * <li><b>Temp Token:</b> System generates temporary token for MFA verification</li>
 * <li><b>MFA Verification:</b> Administrator completes MFA using Ezkey</li>
 * <li><b>Bearer Token:</b> System exchanges temp token for bearer token</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Features:</b>
 * <ul>
 * <li><b>Short Expiration:</b> Temporary tokens expire quickly (5-10 minutes)</li>
 * <li><b>Single Use:</b> Each token can only be used once</li>
 * <li><b>MFA Required:</b> Tokens are only valid when MFA is required</li>
 * <li><b>Audit Trail:</b> All temporary token usage is tracked</li>
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
 * @see EzkeyAdmin
 */
@Entity
@Table(name = "ezkey_admin_temp_tokens")
public class AdminTempToken {

    /**
     * Primary key identifier for the temporary token.
     * <p>
     * This field is auto-generated using the database identity column.
     * </p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "temp_token_id")
    private Integer tempTokenId;

    /**
     * The temporary token string used for MFA verification.
     * <p>
     * This field contains the actual temporary token string that is used
     * during the MFA authentication flow.
     * </p>
     */
    @Column(name = "temp_token", nullable = false, length = 255, unique = true)
    private String tempToken;

    /**
     * Reference to the administrator who owns this temporary token.
     * <p>
     * This field establishes the relationship between the temporary token
     * and the administrator who is completing the MFA flow.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private EzkeyAdmin admin;

    /**
     * Timestamp when the temporary token was created.
     * <p>
     * This field is automatically set to the current timestamp
     * when the temporary token is created.
     * </p>
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the temporary token expires.
     * <p>
     * This field determines when the temporary token becomes invalid
     * and can no longer be used for MFA verification.
     * </p>
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Flag indicating whether MFA is required for this token.
     * <p>
     * This field determines if the administrator must complete MFA
     * to exchange this temporary token for a bearer token.
     * </p>
     */
    @Column(name = "mfa_required", nullable = false)
    private Boolean mfaRequired = true;

    /**
     * Flag indicating whether the temporary token is active.
     * <p>
     * Inactive temporary tokens cannot be used for MFA verification
     * but are preserved for audit purposes.
     * </p>
     */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * Default constructor for JPA.
     * <p>
     * This constructor is required by JPA and should not be used
     * for business logic. Use the parameterized constructor instead.
     * </p>
     */
    public AdminTempToken() {
        // Default constructor for JPA
    }

    /**
     * Constructs a new temporary token with the specified details.
     * <p>
     * This constructor creates a new temporary token with the provided
     * token string, administrator, and expiration time.
     * </p>
     *
     * @param tempToken the temporary token string
     * @param admin the administrator who owns this token
     * @param expiresAt the expiration timestamp
     */
    public AdminTempToken(String tempToken, EzkeyAdmin admin, LocalDateTime expiresAt) {
        this.tempToken = tempToken;
        this.admin = admin;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.mfaRequired = true;
        this.active = true;
    }

    /**
     * Gets the temporary token ID.
     *
     * @return the temporary token ID
     */
    public Integer getTempTokenId() {
        return tempTokenId;
    }

    /**
     * Sets the temporary token ID.
     *
     * @param tempTokenId the temporary token ID
     */
    public void setTempTokenId(Integer tempTokenId) {
        this.tempTokenId = tempTokenId;
    }

    /**
     * Gets the temporary token.
     *
     * @return the temporary token
     */
    public String getTempToken() {
        return tempToken;
    }

    /**
     * Sets the temporary token.
     *
     * @param tempToken the temporary token
     */
    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
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
     * Gets the active status.
     *
     * @return true if the temporary token is active
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
     * Returns a string representation of the temporary token.
     *
     * @return string representation of the temporary token
     */
    @Override
    public String toString() {
        return "AdminTempToken{" +
                "tempTokenId=" + tempTokenId +
                ", tempToken='" + tempToken + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", mfaRequired=" + mfaRequired +
                ", active=" + active +
                '}';
    }
}
