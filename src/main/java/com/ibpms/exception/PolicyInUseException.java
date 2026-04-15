package com.ibpms.exception;

public class PolicyInUseException extends RuntimeException {
    public PolicyInUseException(String message) {
        super(message);
    }
}

