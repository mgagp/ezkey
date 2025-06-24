package org.ezkey.authattempt.dto;

import java.time.LocalDateTime;

public class EzkeyAuthAttemptDto {
	private Integer id;
	private Integer enrollmentId;
	private Integer challenge;
	private Boolean deviceRead;
	private Boolean deviceReplied;
	private Boolean deviceAccepted;
	private String integrationCode;
	private LocalDateTime createdAt;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getEnrollmentId() {
		return enrollmentId;
	}

	public void setEnrollmentId(Integer enrollmentId) {
		this.enrollmentId = enrollmentId;
	}

	public Boolean getDeviceRead() {
		return deviceRead;
	}

	public void setDeviceRead(Boolean deviceRead) {
		this.deviceRead = deviceRead;
	}

	public Boolean getDeviceReplied() {
		return deviceReplied;
	}

	public void setDeviceReplied(Boolean deviceReplied) {
		this.deviceReplied = deviceReplied;
	}

	public Boolean getDeviceAccepted() {
		return deviceAccepted;
	}

	public void setDeviceAccepted(Boolean deviceAccepted) {
		this.deviceAccepted = deviceAccepted;
	}

	public String getIntegrationCode() {
		return integrationCode;
	}

	public void setIntegrationCode(String integrationCode) {
		this.integrationCode = integrationCode;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Integer getChallenge() {
		return challenge;
	}

	public void setChallenge(Integer challenge) {
		this.challenge = challenge;
	}
}