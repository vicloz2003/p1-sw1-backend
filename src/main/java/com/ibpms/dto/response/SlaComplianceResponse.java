package com.ibpms.dto.response;

/**
 * Analytics response for RF-A4: SLA compliance per node.
 * A task complies with the SLA if its completion time is <= slaSeconds
 * defined in the node's metadata.
 */
public record SlaComplianceResponse(
        String nodeId,
        String nodeLabel,
        String policyId,
        String policyName,
        long slaSeconds,
        long totalTasks,
        long withinSla,
        double complianceRate
) {}
