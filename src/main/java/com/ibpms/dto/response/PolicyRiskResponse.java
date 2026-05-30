package com.ibpms.dto.response;

import java.util.List;

/**
 * Consolidated risk report for a business policy: every ACTIVE instance scored by the
 * unsupervised DL engine, sorted highest-risk first (RF-3).
 */
public record PolicyRiskResponse(
        String policyId,
        String policyName,
        int assessedCount,
        long anomalies,
        double threshold,
        String modelInfo,
        List<RiskInstanceResponse> instances
) {}
