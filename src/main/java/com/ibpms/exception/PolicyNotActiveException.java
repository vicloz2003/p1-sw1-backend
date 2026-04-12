package com.ibpms.exception;

public class PolicyNotActiveException extends RuntimeException {
    public PolicyNotActiveException(String policyId) {
        super("Policy is not active: " + policyId);
    }
}

