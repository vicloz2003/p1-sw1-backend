package com.ibpms.dto.response;

import com.ibpms.domain.DocumentRequirement;

import java.util.List;

/**
 * Result of the agent classifying a client's request into a business policy (RF-2.1),
 * enriched by the backend with the documents the client must provide (RF-2.5).
 */
public record AgentClassifyResponse(
        String policyId,
        String policyName,
        double confidence,
        boolean confident,
        List<PolicyMatch> alternatives,
        String message,
        /** Mandatory PROCESS_START documents for the recommended policy (RF-2.5). */
        List<DocumentRequirement> requiredDocuments
) {
    public record PolicyMatch(
            String policyId,
            String policyName,
            double score
    ) {}
}
