package com.ibpms.exception;

/**
 * Thrown when a user attempts to operate on a task they do not own
 * (e.g. completing a task assigned to another user or department).
 * Maps to HTTP 403 Forbidden.
 */
public class TaskAccessDeniedException extends RuntimeException {
    public TaskAccessDeniedException(String message) {
        super(message);
    }
}
