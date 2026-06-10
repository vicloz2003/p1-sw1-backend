package com.ibpms.dto.response;

import java.util.List;

/**
 * Advisory route recommendation for a decision node (RF-3.1), produced by the DL route
 * predictor in ibpms_ml. <b>Advisory only</b> — the deterministic SpEL engine still executes
 * the actual transition; this is decision support shown to the functionary/admin.
 */
public record RouteAdvisoryResponse(
        String processInstanceId,
        String decisionNodeId,
        String decisionNodeLabel,
        String recommendedBranchId,
        String recommendedLabel,
        double confidence,
        boolean confident,
        List<BranchScore> ranking,
        String rationale,
        String modelInfo
) {
    public record BranchScore(String branchId, String label, double probability) {}
}
