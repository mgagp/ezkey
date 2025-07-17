/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain IntegrationCreateResponse
 * Description: Response for creating new Integration entities.
 */

package org.ezkey.integration.domain;

/**
 * Response DTO for the creation of a new Integration entity.
 * <p>
 * This DTO object contains the identifier of the newly created Integration in the system.
 * </p>
 *
 * <p>
 * <b>Usage:</b> Returned by controller methods after a successful Integration creation.
 * </p>
 * *
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
public class IntegrationCreateResponse {

    /**
     * The unique identifier of the newly created Integration entity.
     */
    private Integer id;

    /**
     * Gets the unique identifier of the newly created Integration entity.
     *
     * @return the Integration entity ID
     */
    public Integer getId(){
        return id;
    }

    /**
     * Sets the unique identifier of the newly created Integration entity.
     *
     * @param id the Integration entity ID
     */
    public void setId(Integer id){
        this.id = id;
    }

}