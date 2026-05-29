package com.ibpms.exception;

import java.util.List;

/**
 * Thrown when a BusinessPolicy diagram fails structural validation
 * before being published (HTTP 422 Unprocessable Entity).
 */
public class DiagramInvalidException extends RuntimeException {

    private final List<String> violations;

    public DiagramInvalidException(List<String> violations) {
        super("Diagram has " + violations.size() + " structural violation(s)");
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
