package org.ezkey.integration.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ezkey_integration_i18n")
public class EzkeyIntegrationI18n {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_i18n_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private EzkeyIntegration integration;

    @Column(name = "integration_i18n_lang")
    private String language;

    @Column(name = "integration_i18n_name")
    private String name;

    @Column(name = "integration_i18n_description")
    private String description;

    // Getters and setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public EzkeyIntegration getIntegration() { return integration; }
    public void setIntegration(EzkeyIntegration integration) { this.integration = integration; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
} 