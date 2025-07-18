package org.ezkey.enrollment.dto;

public class EnrollmentVerifyResponseDto {

    private boolean active;

    public EnrollmentVerifyResponseDto(){
    }

    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active = active;
    }
}
