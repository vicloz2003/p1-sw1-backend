package com.ibpms.dto.response;

import java.util.List;

/**
 * Risk assessment of a single ACTIVE process instance (RF-3.2/3.3/3.4), as returned by the
 * ibpms_ml autoencoder and enriched by the gateway.
 */
public record RiskInstanceResponse(
        String processInstanceId,
        String clientId,
        String currentNodeId,
        String currentNodeLabel,
        double elapsedHours,
        double riskScore,
        double delayProbability,
        boolean anomaly,
        String priority,
        double priorityScore,
        List<String> drivers,
        String recommendation
) {}
