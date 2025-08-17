package org.ezkey.enrollment.domain;

public class EnrollmentBindRequest {

    private Integer enrollmentId;

    /**
     * The language code (e.g., "en", "fr") requested for i18n fields.
     * Used to fetch localized integration/application names.
     */
    private String language;

    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the language code for i18n.
     *
     * @return the language code (e.g., "en", "fr")
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the language code for i18n.
     *
     * @param language the language code to set
     */
    public void setLanguage(String language) {
        this.language = language;
    }

}