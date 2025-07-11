package org.ezkey.authattempt.dto;

public class AuthAttemptInitiateResponseDto {
	private Integer authAttemptId;
	private String authAttemptCode;
	private String authAttemptCodeSigned;
	private Boolean authAttemptChallengeRequired;

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
	 * @return the authAttemptCodeSigned
	 */
	public String getAuthAttemptCodeSigned() {
		return authAttemptCodeSigned;
	}

	/**
	 * @param authAttemptCodeSigned the authAttemptCodeSigned to set
	 */
	public void setAuthAttemptCodeSigned(String authAttemptCodeSigned) {
		this.authAttemptCodeSigned = authAttemptCodeSigned;
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

}