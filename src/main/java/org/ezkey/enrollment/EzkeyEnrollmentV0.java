package org.ezkey.enrollment;

import java.time.LocalDateTime;

public class EzkeyEnrollmentV0 {

	private Integer enrollmentId;
	private Integer integrationId;
	private String enrollmentName;
	private Boolean enrollmentRead;
	private Boolean enrollmentConfirmed;
	private Boolean enrollmentActive;
	private Integer enrollmentChallenge;
	private Boolean authAttemptChallengeRequired;
	private String integrationPrivateKey;
	private String integrationPublicKey;
	private String authAttemptPublicKey;
	private String enrollmentCode;
	private LocalDateTime createdAt;

	/**
	 * @return the enrollmentId
	 */
	public Integer getEnrollmentId() {
		return enrollmentId;
	}

	/**
	 * @param enrollmentId the enrollmentId to set
	 */
	public void setEnrollmentId(Integer enrollmentId) {
		this.enrollmentId = enrollmentId;
	}

	/**
	 * @return the integrationId
	 */
	public Integer getIntegrationId() {
		return integrationId;
	}

	/**
	 * @param integrationId the integrationId to set
	 */
	public void setIntegrationId(Integer integrationId) {
		this.integrationId = integrationId;
	}

	/**
	 * @return the enrollmentName
	 */
	public String getEnrollmentName() {
		return enrollmentName;
	}

	/**
	 * @param enrollmentName the enrollmentName to set
	 */
	public void setEnrollmentName(String enrollmentName) {
		this.enrollmentName = enrollmentName;
	}

	/**
	 * @return the enrollmentRead
	 */
	public Boolean getEnrollmentRead() {
		return enrollmentRead;
	}

	/**
	 * @param enrollmentRead the enrollmentRead to set
	 */
	public void setEnrollmentRead(Boolean enrollmentRead) {
		this.enrollmentRead = enrollmentRead;
	}

	/**
	 * @return the enrollmentConfirmed
	 */
	public Boolean getEnrollmentConfirmed() {
		return enrollmentConfirmed;
	}

	/**
	 * @param enrollmentConfirmed the enrollmentConfirmed to set
	 */
	public void setEnrollmentConfirmed(Boolean enrollmentConfirmed) {
		this.enrollmentConfirmed = enrollmentConfirmed;
	}

	/**
	 * @return the enrollmentActive
	 */
	public Boolean getEnrollmentActive() {
		return enrollmentActive;
	}

	/**
	 * @param enrollmentActive the enrollmentActive to set
	 */
	public void setEnrollmentActive(Boolean enrollmentActive) {
		this.enrollmentActive = enrollmentActive;
	}

	/**
	 * @return the authAttemptChallengeRequired
	 */
	public Boolean getAuthAttemptChallengeRequired() {
		return authAttemptChallengeRequired;
	}

	/**
	 * @param authAttemptChallengeRequired the authAttemptChallengeRequired to set
	 */
	public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
		this.authAttemptChallengeRequired = authAttemptChallengeRequired;
	}

	/**
	 * @return the enrollmentChallenge
	 */
	public Integer getEnrollmentChallenge() {
		return enrollmentChallenge;
	}

	/**
	 * @param enrollmentChallenge the enrollmentChallenge to set
	 */
	public void setEnrollmentChallenge(Integer enrollmentChallenge) {
		this.enrollmentChallenge = enrollmentChallenge;
	}

	/**
	 * @return the integrationPrivateKey
	 */
	public String getIntegrationPrivateKey() {
		return integrationPrivateKey;
	}

	/**
	 * @param integrationPrivateKey the integrationPrivateKey to set
	 */
	public void setIntegrationPrivateKey(String integrationPrivateKey) {
		this.integrationPrivateKey = integrationPrivateKey;
	}

	/**
	 * @return the integrationPublicKey
	 */
	public String getIntegrationPublicKey() {
		return integrationPublicKey;
	}

	/**
	 * @param integrationPublicKey the integrationPublicKey to set
	 */
	public void setIntegrationPublicKey(String integrationPublicKey) {
		this.integrationPublicKey = integrationPublicKey;
	}

	/**
	 * @return the enrollmentCode
	 */
	public String getEnrollmentCode() {
		return enrollmentCode;
	}

	/**
	 * @param enrollmentCode the enrollmentCode to set
	 */
	public void setEnrollmentCode(String enrollmentCode) {
		this.enrollmentCode = enrollmentCode;
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

	public String getAuthAttemptPublicKey() {
		return authAttemptPublicKey;
	}

	public void setAuthAttemptPublicKey(String authAttemptPublicKey) {
		this.authAttemptPublicKey = authAttemptPublicKey;
	}

}