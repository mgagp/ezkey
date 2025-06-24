package org.ezkey.integration.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ezkey_integration")
public class EzkeyIntegration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_id")
    private Integer id;

    @Column(name = "integration_code")
    private String code;

    @Column(name = "integration_logo")
    private String logo;

    @Column(name = "integration_active")
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "integration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EzkeyIntegrationI18n> i18n;
    
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getLogo() {
        return logo;
    }
    public void setLogo(String logo) {
        this.logo = logo;
    }
    public Boolean getActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public List<EzkeyIntegrationI18n> getI18n() {
        return i18n;
    }
    public void setI18n(List<EzkeyIntegrationI18n> i18n) {
        this.i18n = i18n;
    }
} 