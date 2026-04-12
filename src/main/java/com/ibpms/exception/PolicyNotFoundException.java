package com.ibpms.exception;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String policyId) {
        super("Policy not found: " + policyId);
    }
}

