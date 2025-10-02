/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: AdminTokenAuthenticationFilter
 * Description: Custom authentication filter for validating admin bearer tokens.
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.ezkey.admin.service.AdminTokenValidationService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Custom authentication filter for validating admin bearer tokens.
 * <p>
 * This filter intercepts requests with "Authorization: Bearer" headers,
 * validates the token against the database, and sets up the security context
 * if the token is valid and not expired.
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
@Component
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AdminTokenAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminTokenValidationService tokenValidationService;

    public AdminTokenAuthenticationFilter(AdminTokenValidationService tokenValidationService) {
        this.tokenValidationService = tokenValidationService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            
            try {
                // Use the service for transaction-aware validation
                Optional<EzkeyAdmin> adminOptional = tokenValidationService.validateToken(token);
                
                if (adminOptional.isPresent()) {
                    EzkeyAdmin admin = adminOptional.get();
                    
                    // Create authentication object
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            admin.getUsername(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        );
                    
                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    // Update last used timestamp in a separate transaction
                    tokenValidationService.updateTokenLastUsed(token);
                    
                    logger.debug("✅ Token validated successfully for admin: {}", admin.getUsername());
                }
            } catch (Exception e) {
                logger.error("❌ Error validating token: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
