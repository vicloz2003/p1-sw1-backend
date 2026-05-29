package com.ibpms.dto.response;

/**
 * Analytics response for RF-A5: abandonment rate per policy.
 */
public record AbandonmentResponse(
        String policyId,
        String policyName,
        long totalInitiated,
        long completed,
        long cancelled,
        double abandonmentRate
) {}
