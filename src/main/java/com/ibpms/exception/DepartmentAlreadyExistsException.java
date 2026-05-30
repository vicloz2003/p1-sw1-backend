package com.ibpms.exception;

/**
 * Thrown when attempting to create a department whose normalized name already
 * exists (HTTP 409 Conflict).
 */
public class DepartmentAlreadyExistsException extends RuntimeException {
    public DepartmentAlreadyExistsException(String normalizedName) {
        super("Ya existe un departamento con el nombre: " + normalizedName);
    }
}
