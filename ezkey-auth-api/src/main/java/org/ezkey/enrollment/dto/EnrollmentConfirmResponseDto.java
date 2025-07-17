package org.ezkey.enrollment.dto;

public class EnrollmentConfirmResponseDto {

    private boolean active;

    public EnrollmentConfirmResponseDto(){
    }

    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active = active;
    }
}
