package org.ezkey.enrollment.domain;

public class EnrollmentConfirmResponse {

    private boolean active;

    public EnrollmentConfirmResponse(){
    }

    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active = active;
    }
}
