/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EzkeyAuthAttemptDto
 * Description: Response DTO for authorization attempt data.
 */

package org.ezkey.authattempt.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for authorization attempt data.
 * <p>
 * This DTO represents the response data for authorization attempts,
 * excluding sensitive information like private keys for security.
 * It provides a clean API interface for authorization attempt operations.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> API response for authorization attempt operations</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AuthAttemptDto {

	private Integer authAttemptId;
	private Integer enrollmentId;
	private Boolean authAttemptRead;
	private Boolean authAttemptReplied;
	private Boolean authAttemptAccepted;
	private Integer authAttemptChallenge;
	private String authAttemptCode;
	private LocalDateTime createdAt;

	/**
	 * Default constructor.
	 */
	public AuthAttemptDto() {
	}

	/**
	 * Constructs an authorization attempt DTO with all fields.
	 *
	 * @param authAttemptId the authorization attempt ID
	 * @param enrollmentId the enrollment ID
	 * @param authAttemptRead whether the attempt has been read
	 * @param authAttemptReplied whether the attempt has been replied to
	 * @param authAttemptAccepted whether the attempt has been accepted
	 * @param authAttemptChallenge the challenge code
	 * @param authAttemptCode the authorization attempt code
	 * @param createdAt the creation timestamp
	 */
	public AuthAttemptDto(Integer authAttemptId, Integer enrollmentId, Boolean authAttemptRead,
							   Boolean authAttemptReplied, Boolean authAttemptAccepted, Integer authAttemptChallenge,
							   String authAttemptCode, LocalDateTime createdAt) {
		this.authAttemptId = authAttemptId;
		this.enrollmentId = enrollmentId;
		this.authAttemptRead = authAttemptRead;
		this.authAttemptReplied = authAttemptReplied;
		this.authAttemptAccepted = authAttemptAccepted;
		this.authAttemptChallenge = authAttemptChallenge;
		this.authAttemptCode = authAttemptCode;
		this.createdAt = createdAt;
	}

	// Getters and Setters

	/**
	 * Gets the authorization attempt ID.
	 *
	 * @return the authorization attempt ID
	 */
	public Integer getAuthAttemptId() {
		return authAttemptId;
	}

	/**
	 * Sets the authorization attempt ID.
	 *
	 * @param authAttemptId the authorization attempt ID to set
	 */
	public void setAuthAttemptId(Integer authAttemptId) {
		this.authAttemptId = authAttemptId;
	}

	/**
	 * Gets the enrollment ID.
	 *
	 * @return the enrollment ID
	 */
	public Integer getEnrollmentId() {
		return enrollmentId;
	}

	/**
	 * Sets the enrollment ID.
	 *
	 * @param enrollmentId the enrollment ID to set
	 */
	public void setEnrollmentId(Integer enrollmentId) {
		this.enrollmentId = enrollmentId;
	}

	/**
	 * Gets whether the authorization attempt has been read.
	 *
	 * @return true if the attempt has been read, false otherwise
	 */
	public Boolean getAuthAttemptRead() {
		return authAttemptRead;
	}

	/**
	 * Sets whether the authorization attempt has been read.
	 *
	 * @param authAttemptRead true if the attempt has been read, false otherwise
	 */
	public void setAuthAttemptRead(Boolean authAttemptRead) {
		this.authAttemptRead = authAttemptRead;
	}

	/**
	 * Gets whether the authorization attempt has been replied to.
	 *
	 * @return true if the attempt has been replied to, false otherwise
	 */
	public Boolean getAuthAttemptReplied() {
		return authAttemptReplied;
	}

	/**
	 * Sets whether the authorization attempt has been replied to.
	 *
	 * @param authAttemptReplied true if the attempt has been replied to, false otherwise
	 */
	public void setAuthAttemptReplied(Boolean authAttemptReplied) {
		this.authAttemptReplied = authAttemptReplied;
	}

	/**
	 * Gets whether the authorization attempt has been accepted.
	 *
	 * @return true if the attempt has been accepted, false otherwise
	 */
	public Boolean getAuthAttemptAccepted() {
		return authAttemptAccepted;
	}

	/**
	 * Sets whether the authorization attempt has been accepted.
	 *
	 * @param authAttemptAccepted true if the attempt has been accepted, false otherwise
	 */
	public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
		this.authAttemptAccepted = authAttemptAccepted;
	}

	/**
	 * Gets the authorization attempt challenge.
	 *
	 * @return the challenge code
	 */
	public Integer getAuthAttemptChallenge() {
		return authAttemptChallenge;
	}

	/**
	 * Sets the authorization attempt challenge.
	 *
	 * @param authAttemptChallenge the challenge code to set
	 */
	public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
		this.authAttemptChallenge = authAttemptChallenge;
	}

	/**
	 * Gets the authorization attempt code.
	 *
	 * @return the authorization attempt code
	 */
	public String getAuthAttemptCode() {
		return authAttemptCode;
	}

	/**
	 * Sets the authorization attempt code.
	 *
	 * @param authAttemptCode the authorization attempt code to set
	 */
	public void setAuthAttemptCode(String authAttemptCode) {
		this.authAttemptCode = authAttemptCode;
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
	 * @param createdAt the creation timestamp to set
	 */
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public String toString() {
		return "EzkeyAuthAttemptDto{" +
				"authAttemptId=" + authAttemptId +
				", enrollmentId=" + enrollmentId +
				", authAttemptRead=" + authAttemptRead +
				", authAttemptReplied=" + authAttemptReplied +
				", authAttemptAccepted=" + authAttemptAccepted +
				", authAttemptChallenge=" + authAttemptChallenge +
				", authAttemptCode='" + authAttemptCode + '\'' +
				", createdAt=" + createdAt +
				'}';
	}
}