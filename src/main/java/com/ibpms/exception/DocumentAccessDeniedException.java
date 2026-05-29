package com.ibpms.exception;

public class DocumentAccessDeniedException extends RuntimeException {
    public DocumentAccessDeniedException(String message) {
        super(message);
    }
}
