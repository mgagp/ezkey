/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminUserDetailsService
 * Description: Custom UserDetailsService for Spring Security integration with database.
 */

package org.ezkey.admin.service;

import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom UserDetailsService for Spring Security integration.
 * <p>
 * This service provides user details from the database for Spring Security authentication.
 * It implements the standard Spring Security UserDetailsService interface and integrates
 * with our EzkeyAdmin entity to provide authentication services.
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
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserDetailsService.class);

    @Autowired
    private EzkeyAdminRepository adminRepository;

    /**
     * Load user details by username for Spring Security authentication.
     * <p>
     * This method is called by Spring Security when a user attempts to authenticate.
     * It retrieves the admin user from the database and converts it to a Spring Security
     * UserDetails object with appropriate authorities and account status.
     * </p>
     *
     * @param username the username to load
     * @return UserDetails object for Spring Security
     * @throws UsernameNotFoundException if the user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("🔍 Loading user details for username: {}", username);
        
        try {
            // Find admin in database
            EzkeyAdmin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("❌ Admin not found for username: {}", username);
                    return new UsernameNotFoundException("Admin not found: " + username);
                });
            
            logger.info("✅ Admin found - ID: {}, Type: {}, Active: {}", 
                admin.getAdminId(), admin.getAdminType(), admin.getActive());
            
            // Build Spring Security UserDetails
            User.UserBuilder userBuilder = User.builder()
                .username(admin.getUsername())
                .password(admin.getPasswordHash())
                .authorities("ROLE_ADMIN")
                .accountExpired(!admin.getActive())
                .accountLocked(!admin.getActive())
                .credentialsExpired(admin.getPasswordChangeRequired())
                .disabled(!admin.getActive());
            
            UserDetails userDetails = userBuilder.build();
            logger.info("✅ UserDetails created successfully for username: {}", username);
            
            return userDetails;
            
        } catch (Exception e) {
            logger.error("❌ Error loading user details for username: {} - Error: {}", 
                username, e.getMessage());
            throw new UsernameNotFoundException("Error loading user: " + username, e);
        }
    }
}
