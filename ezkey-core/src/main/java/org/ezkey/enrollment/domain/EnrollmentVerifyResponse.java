package org.ezkey.enrollment.domain;

public class EnrollmentVerifyResponse {

    private boolean active;

    public EnrollmentVerifyResponse(){
    }

    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active = active;
    }
}
