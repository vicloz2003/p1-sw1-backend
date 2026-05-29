package com.ibpms.dto.response;

import java.util.List;

/**
 * Analytics response for RF-A6: consolidated KPI dashboard per policy.
 */
public record AnalyticsDashboardResponse(
        String policyId,
        String policyName,
        long activeInstances,
        long completedInstances,
        long cancelledInstances,
        double abandonmentRate,
        double avgCompletionTimeHours,
        List<BottleneckResponse> topBottlenecks,
        List<EmployeePerformanceResponse> poorPerformers
) {}
