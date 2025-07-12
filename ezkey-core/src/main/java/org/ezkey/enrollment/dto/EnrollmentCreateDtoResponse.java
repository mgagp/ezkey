package org.ezkey.enrollment.dto;

public class EnrollmentCreateDtoResponse {
    private Integer id;

    private Integer challenge;

    public Integer getId(){
        return id;
    }

    public void setId(Integer id){
        this.id = id;
    }

    public Integer getChallenge(){
        return challenge;
    }

    public void setChallenge(Integer challenge){
        this.challenge = challenge;
    }
}