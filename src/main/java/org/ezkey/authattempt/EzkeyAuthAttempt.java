/**
 * This is the EzkeyAuthAttempt class, which represents an authorization attempt
 * within the EzKey system. It includes attributes and methods to manage the state and metadata
 * of an authorization attempt, such as enrollment details, challenge status, and time stamps.
 *
 * The EzkeyAuthAttempt class is a core part of the authorization process and interacts with
 * other components in the EzKey application, such as enrollment and integration services.
 *
 * @author EzKey Development Team
 * @version 1.0
 * @since 2025-06-13
 */
package org.ezkey.authattempt;

import java.time.LocalDateTime;

/**
 * Represents an authorization attempt within the Ezkey system. This class
 * contains details about the attempt, including its status and associated
 * metadata.
 */
public class EzkeyAuthAttempt {

	/**
	 * Identifier for the authorization attempt.
	 */
	private Integer authAttemptId;

	/**
	 * Identifier for the enrollment associated with this authorization attempt.
	 */
	private Integer enrollmentId;

	/**
	 * Challenge code sent to the device for verification.
	 */
	private Integer authAttemptChallenge;

	/**
	 * Indicates whether the device has read the challenge.
	 */
	private Boolean authAttemptRead;

	/**
	 * Indicates whether the device has replied to the challenge.
	 */
	private Boolean authAttemptReplied;

	/**
	 * Indicates whether the device has accepted the challenge.
	 */
	private Boolean authAttemptAccepted;

	/**
	 * Code used for integration purposes.
	 */
	private String authAttemptCode;

	/**
	 * Timestamp when the authorization attempt was created.
	 */
	private LocalDateTime createdAt;

	/**
	 * @return the authAttemptId
	 */
	public Integer getAuthAttemptId() {
		return authAttemptId;
	}

	/**
	 * @param authAttemptId the authAttemptId to set
	 */
	public void setAuthAttemptId(Integer authAttemptId) {
		this.authAttemptId = authAttemptId;
	}

	/**
	 * @return the authAttemptChallenge
	 */
	public Integer getAuthAttemptChallenge() {
		return authAttemptChallenge;
	}

	/**
	 * @param authAttemptChallenge the authAttemptChallenge to set
	 */
	public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
		this.authAttemptChallenge = authAttemptChallenge;
	}

	/**
	 * @return the authAttemptRead
	 */
	public Boolean getAuthAttemptRead() {
		return authAttemptRead;
	}

	/**
	 * @param authAttemptRead the authAttemptRead to set
	 */
	public void setAuthAttemptRead(Boolean authAttemptRead) {
		this.authAttemptRead = authAttemptRead;
	}

	/**
	 * @return the authAttemptReplied
	 */
	public Boolean getAuthAttemptReplied() {
		return authAttemptReplied;
	}

	/**
	 * @param authAttemptReplied the authAttemptReplied to set
	 */
	public void setAuthAttemptReplied(Boolean authAttemptReplied) {
		this.authAttemptReplied = authAttemptReplied;
	}

	/**
	 * @return the authAttemptAccepted
	 */
	public Boolean getAuthAttemptAccepted() {
		return authAttemptAccepted;
	}

	/**
	 * @param authAttemptAccepted the authAttemptAccepted to set
	 */
	public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
		this.authAttemptAccepted = authAttemptAccepted;
	}

	/**
	 * @return the authAttemptCode
	 */
	public String getAuthAttemptCode() {
		return authAttemptCode;
	}

	/**
	 * @param authAttemptCode the authAttemptCode to set
	 */
	public void setAuthAttemptCode(String authAttemptCode) {
		this.authAttemptCode = authAttemptCode;
	}

	/**
	 * @return the createdAt
	 */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/**
	 * @param createdAt the createdAt to set
	 */
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Retrieves the enrollment ID associated with this authorization attempt.
	 * 
	 * @return the enrollment ID
	 */
	public Integer getEnrollmentId() {
		return enrollmentId;
	}

	/**
	 * Sets the enrollment ID for this authorization attempt.
	 * 
	 * @param enrollmentId the enrollment ID to set
	 */
	public void setEnrollmentId(Integer enrollmentId) {
		this.enrollmentId = enrollmentId;
	}

}