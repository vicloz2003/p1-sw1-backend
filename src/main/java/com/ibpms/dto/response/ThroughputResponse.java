package com.ibpms.dto.response;

/**
 * Analytics response for RF-A3: process throughput per policy and period.
 */
public record ThroughputResponse(
        String period,
        String policyId,
        String policyName,
        long initiated,
        long completed,
        long cancelled,
        double completionRate
) {}
