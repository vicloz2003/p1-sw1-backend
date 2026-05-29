package com.ibpms.dto.response;

/**
 * Analytics response for RF-A2: employee performance metrics.
 * performanceRatio > 1.0 means the employee is SLOWER than the department average
 * for the same node types. Values are ordered worst-first (highest ratio first).
 */
public record EmployeePerformanceResponse(
        String userId,
        String username,
        String departmentId,
        double avgCompletionSeconds,
        double globalAvgForSameNodes,
        double performanceRatio,
        long taskCount,
        String performanceLevel    // "GOOD" | "AVERAGE" | "POOR"
) {}
