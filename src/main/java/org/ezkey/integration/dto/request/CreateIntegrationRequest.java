package org.ezkey.integration.dto.request;

import java.util.List;
import org.ezkey.integration.dto.response.IntegrationI18nResponse;

public class CreateIntegrationRequest {
    private String code;
    private String logo;
    private List<IntegrationI18nResponse> i18n;

    // Getters and setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public List<IntegrationI18nResponse> getI18n() { return i18n; }
    public void setI18n(List<IntegrationI18nResponse> i18n) { this.i18n = i18n; }
} 