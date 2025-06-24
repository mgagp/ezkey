package org.ezkey.integration.exception;

/**
 * Exception thrown when a requested resource is not found.
 * <p>
 * This exception is used to indicate that a requested entity (Integration, Enrollment, etc.)
 * could not be found in the system.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class ResourceNotFoundException extends RuntimeException {
    
    /**
     * Constructs a new ResourceNotFoundException with a formatted message.
     *
     * @param resource the type of resource that was not found
     * @param id the identifier that was used to search for the resource
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s with id %s not found", resource, id));
    }
} 