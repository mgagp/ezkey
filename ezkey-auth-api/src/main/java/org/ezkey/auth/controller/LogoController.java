/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: LogoController
 * Description: REST controller for serving logo resources through HTTP endpoints.
 */

package org.ezkey.auth.controller;

import java.util.Base64;

import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.LogoResponse;
import org.ezkey.integration.service.LogoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for serving logo resources in auth API.
 * <p>
 * This controller provides endpoints for retrieving logo images that can be
 * used by mobile applications. It supports both URL-based logos and base64-encoded
 * logo data stored in the database.
 * </p>
 *
 * <p>
 * <b>Auth API Endpoints (External):</b>
 * <ul>
 * <li><b>GET /api/v1/logos/{id}/image</b> - Get logo image by ID</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the auth-api for mobile device consumption.
 * Logos are used in the enrollment binding process to display integration branding.
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
@RestController
@RequestMapping("/api/v1/logos")
@Tag(name = "Logos", description = "Logo resource serving API")
public class LogoController {

    private final LogoService logoService;

    /**
     * Constructs the controller with required dependencies.
     *
     * @param logoService the service layer for Logo operations
     */
    public LogoController(LogoService logoService) {
        this.logoService = logoService;
    }

    /**
     * Retrieves a logo image by its ID.
     * <p>
     * If the logo is stored as a URL, returns a redirect to that URL.
     * If the logo is stored as base64-encoded data, decodes and returns the image directly.
     * </p>
     *
     * @param id the unique identifier of the Logo to retrieve
     * @return ResponseEntity containing the logo image data or redirect
     * @throws ResourceNotFoundException if the Logo with the given id is not found (returns HTTP 404)
     */
    @Operation(summary = "Get logo image by ID", description = "Returns the logo image for the specified ID")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Logo image found and returned"),
                    @ApiResponse(responseCode = "302", description = "Redirect to external logo URL"),
                    @ApiResponse(responseCode = "404", description = "Logo not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getLogoImage(
            @Parameter(description = "Unique logo ID", example = "1") @PathVariable("id") Integer id) {
        LogoResponse logo = logoService.getLogoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Logo", id));

        // If logo URL is provided, redirect to it
        if (logo.getLogoUrl() != null && !logo.getLogoUrl().trim().isEmpty()) {
            return ResponseEntity
                    .status(302)
                    .header(HttpHeaders.LOCATION, logo.getLogoUrl())
                    .build();
        }

        // If logo data is provided, decode and return it
        if (logo.getLogoData() != null && !logo.getLogoData().trim().isEmpty()) {
            try {
                String logoData = logo.getLogoData();
                
                // Handle data URL format (data:image/png;base64,...)
                if (logoData.startsWith("data:")) {
                    int commaIndex = logoData.indexOf(',');
                    if (commaIndex != -1) {
                        logoData = logoData.substring(commaIndex + 1);
                    }
                }
                
                byte[] imageBytes = Base64.getDecoder().decode(logoData);
                
                // Determine content type
                MediaType contentType = MediaType.IMAGE_PNG; // default
                if (logo.getContentType() != null && !logo.getContentType().trim().isEmpty()) {
                    try {
                        contentType = MediaType.parseMediaType(logo.getContentType());
                    } catch (Exception e) {
                        // Use default if parsing fails
                    }
                }
                
                return ResponseEntity
                        .ok()
                        .contentType(contentType)
                        .body(imageBytes);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 logo data for logo ID " + id);
            }
        }

        throw new ResourceNotFoundException("Logo image data not found for logo", id);
    }
}
