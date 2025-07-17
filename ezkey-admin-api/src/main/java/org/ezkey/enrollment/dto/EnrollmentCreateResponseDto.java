package org.ezkey.enrollment.dto;

public class EnrollmentCreateResponseDto {

    private Integer enrollmentId;

    private Integer enrollmentChallenge;

    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    public Integer getEnrollmentChallenge(){
        return enrollmentChallenge;
    }

    public void setEnrollmentChallenge(Integer enrollmentChallenge){
        this.enrollmentChallenge = enrollmentChallenge;
    }

}