package com.ibpms.exception;

/**
 * Thrown when the ibpms_ml microservice (agent classification / transcription)
 * is unreachable or returns an error. Maps to HTTP 503 Service Unavailable.
 */
public class AgentUnavailableException extends RuntimeException {
    public AgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
