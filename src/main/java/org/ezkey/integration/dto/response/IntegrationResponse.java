package org.ezkey.integration.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class IntegrationResponse {
    private Integer id;
    private String code;
    private String logo;
    private Boolean active;
    private LocalDateTime createdAt;
    private List<IntegrationI18nResponse> i18n;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<IntegrationI18nResponse> getI18n() { return i18n; }
    public void setI18n(List<IntegrationI18nResponse> i18n) { this.i18n = i18n; }
} 