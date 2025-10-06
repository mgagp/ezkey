/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: PasswordChangeRequiredFilterConfig
 * Description: Configuration for registering the password change required filter.
 */

package org.ezkey.admin.config;

import org.ezkey.admin.filter.PasswordChangeRequiredFilter;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for registering the PasswordChangeRequiredFilter.
 * <p>
 * This configuration ensures that the filter is properly registered with the
 * servlet container and applied to all admin API endpoints.
 * </p>
 *
 * <p>
 * <b>Filter Order:</b>
 * The filter is registered with order 2 to run after authentication filters
 * but before application logic. This ensures that:
 * <ul>
 * <li>Authentication has already been performed</li>
 * <li>Admin identity is established</li>
 * <li>Password change requirement can be checked</li>
 * <li>Request can be blocked before reaching controllers</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
public class PasswordChangeRequiredFilterConfig {

    /**
     * Creates the PasswordChangeRequiredFilter bean.
     *
     * @param adminTokenRepository repository for admin token operations
     * @return the password change required filter instance
     */
    @Bean
    public PasswordChangeRequiredFilter passwordChangeRequiredFilter(
            AdminTokenRepository adminTokenRepository) {
        return new PasswordChangeRequiredFilter(adminTokenRepository);
    }

    /**
     * Registers the PasswordChangeRequiredFilter with the servlet container.
     * <p>
     * The filter is configured to intercept all admin API requests and enforce
     * the password change requirement before allowing access to protected resources.
     * </p>
     *
     * @param filter the password change required filter instance
     * @return the filter registration bean
     */
    @Bean
    public FilterRegistrationBean<PasswordChangeRequiredFilter> passwordChangeRequiredFilterRegistration(
            PasswordChangeRequiredFilter filter) {
        
        FilterRegistrationBean<PasswordChangeRequiredFilter> registration = 
            new FilterRegistrationBean<>();
        
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1); // Run before other filters to catch requests early
        registration.setName("passwordChangeRequiredFilter");
        
        return registration;
    }
}

