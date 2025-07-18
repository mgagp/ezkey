package org.ezkey.authattempt.dto;

public class AuthAttemptRespondResponseDto {

    private Boolean success;

    private String message;

    public Boolean getSuccess(){
        return success;
    }

    public void setSuccess(Boolean success){
        this.success = success;
    }

    public String getMessage(){
        return message;
    }

    public void setMessage(String message){
        this.message = message;
    }
}