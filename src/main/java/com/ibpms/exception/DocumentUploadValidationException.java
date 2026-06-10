package com.ibpms.exception;

/**
 * Raised when an upload violates the {@link com.ibpms.domain.DocumentRequirement} rules
 * (wrong uploader role, disallowed MIME type, or exceeded max size). Mapped to HTTP 422.
 */
public class DocumentUploadValidationException extends RuntimeException {
    public DocumentUploadValidationException(String message) {
        super(message);
    }
}
