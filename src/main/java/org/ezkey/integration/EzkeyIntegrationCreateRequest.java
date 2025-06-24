package org.ezkey.integration;

import java.util.List;

public class EzkeyIntegrationCreateRequest {
    private String code;
    private String logo;
    private List<EzkeyIntegrationI18nDto> i18n;

    // Getters and setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public List<EzkeyIntegrationI18nDto> getI18n() { return i18n; }
    public void setI18n(List<EzkeyIntegrationI18nDto> i18n) { this.i18n = i18n; }
}
