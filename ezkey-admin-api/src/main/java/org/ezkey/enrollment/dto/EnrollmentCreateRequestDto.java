package org.ezkey.enrollment.dto;

public class EnrollmentCreateRequestDto {

    private Integer integrationId;

    private String name;

    private Boolean authAttemptChallengeRequired;

    public Integer getIntegrationId(){
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId){
        this.integrationId = integrationId;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public Boolean getAuthAttemptChallengeRequired(){
        return authAttemptChallengeRequired;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired){
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}