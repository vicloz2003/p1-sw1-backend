package com.ibpms.exception;

public class ProcessInstanceNotFoundException extends RuntimeException {
    public ProcessInstanceNotFoundException(String message) {
        super(message);
    }
}

