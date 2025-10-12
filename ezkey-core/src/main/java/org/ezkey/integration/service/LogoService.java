/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root
 * for full license information.
 *
 * Service: LogoService
 * Description: Business logic service for managing Logo entities.
 */

package org.ezkey.integration.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.LogoCreateRequest;
import org.ezkey.integration.domain.LogoResponse;
import org.ezkey.integration.domain.entity.Logo;
import org.ezkey.integration.domain.repository.LogoRepository;
import org.ezkey.integration.mapper.LogoServiceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic service for managing Logo entities.
 * <p>
 * This service provides operations for creating, retrieving, updating,
 * and deleting logos in the Ezkey system. Logos can be referenced by
 * integrations to provide consistent branding.
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
public class LogoService {

    private final LogoRepository logoRepository;
    private final LogoServiceMapper mapper;

    /**
     * Constructs the service with required dependencies.
     *
     * @param logoRepository the repository for Logo operations
     * @param mapper the mapper for converting between entities and DTOs
     */
    public LogoService(final LogoRepository logoRepository,
                       final LogoServiceMapper mapper) {
        this.logoRepository = logoRepository;
        this.mapper = mapper;
    }

    /**
     * Creates a new logo.
     *
     * @param request the logo creation request
     * @return the created logo response
     * @throws IllegalArgumentException if the logo name already exists or
     * if neither logoUrl nor logoData is provided
     */
    @Transactional
    public LogoResponse createLogo(final LogoCreateRequest request) {
        // Validate that logo name is unique
        if (logoRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Logo with name '"
                    + request.getName() + "' already exists");
        }

        // Validate that at least one source is provided
        if ((request.getLogoUrl() == null
                || request.getLogoUrl().trim().isEmpty())
                && (request.getLogoData() == null
                || request.getLogoData().trim().isEmpty())) {
            throw new IllegalArgumentException(
                    "Either logoUrl or logoData must be provided");
        }

        Logo logo = mapper.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        logo.setCreatedAt(now);
        logo.setUpdatedAt(now);

        Logo savedLogo = logoRepository.save(logo);
        return mapper.toResponse(savedLogo);
    }

    /**
     * Retrieves all logos.
     *
     * @return list of all logos
     */
    @Transactional(readOnly = true)
    public List<LogoResponse> getAllLogos() {
        List<Logo> logos = logoRepository.findAll();
        return mapper.toResponseList(logos);
    }

    /**
     * Retrieves a logo by its ID.
     *
     * @param id the logo ID
     * @return Optional containing the logo if found
     */
    @Transactional(readOnly = true)
    public Optional<LogoResponse> getLogoById(final Integer id) {
        return logoRepository.findById(id)
                .map(mapper::toResponse);
    }

    /**
     * Retrieves a logo by its name.
     *
     * @param name the logo name
     * @return Optional containing the logo if found
     */
    @Transactional(readOnly = true)
    public Optional<LogoResponse> getLogoByName(final String name) {
        return logoRepository.findByName(name)
                .map(mapper::toResponse);
    }

    /**
     * Updates an existing logo.
     *
     * @param id the logo ID to update
     * @param request the logo update request
     * @return the updated logo response
     * @throws IllegalArgumentException if the logo is not found or if
     * the name conflicts with another logo
     */
    @Transactional
    public LogoResponse updateLogo(final Integer id,
                                   final LogoCreateRequest request) {
        Logo existingLogo = logoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Logo with id " + id + " not found"));

        // Check if name is being changed and if new name already exists
        if (!existingLogo.getName().equals(request.getName())
                && logoRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Logo with name '"
                    + request.getName() + "' already exists");
        }

        // Validate that at least one source is provided
        if ((request.getLogoUrl() == null
                || request.getLogoUrl().trim().isEmpty())
                && (request.getLogoData() == null
                || request.getLogoData().trim().isEmpty())) {
            throw new IllegalArgumentException(
                    "Either logoUrl or logoData must be provided");
        }

        existingLogo.setName(request.getName());
        existingLogo.setLogoUrl(request.getLogoUrl());
        existingLogo.setLogoData(request.getLogoData());
        existingLogo.setContentType(request.getContentType());
        existingLogo.setUpdatedAt(LocalDateTime.now());

        Logo updatedLogo = logoRepository.save(existingLogo);
        return mapper.toResponse(updatedLogo);
    }

    /**
     * Deletes a logo by its ID.
     *
     * @param id the logo ID to delete
     * @throws IllegalArgumentException if the logo is not found
     */
    @Transactional
    public void deleteLogo(final Integer id) {
        if (!logoRepository.existsById(id)) {
            throw new IllegalArgumentException(
                    "Logo with id " + id + " not found");
        }
        logoRepository.deleteById(id);
    }
}
