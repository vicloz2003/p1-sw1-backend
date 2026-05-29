package com.ibpms.service.api;

import com.ibpms.dto.response.AbandonmentResponse;
import com.ibpms.dto.response.AnalyticsDashboardResponse;
import com.ibpms.dto.response.BottleneckResponse;
import com.ibpms.dto.response.EmployeePerformanceResponse;
import com.ibpms.dto.response.SlaComplianceResponse;
import com.ibpms.dto.response.ThroughputResponse;

import java.util.List;

public interface AnalyticsService {

    /** RF-A1: Nodes ordered by average task duration desc. */
    List<BottleneckResponse> getBottlenecks();

    /** RF-A1 (scoped): Bottlenecks filtered to a specific policy. */
    List<BottleneckResponse> getBottlenecksByPolicy(String policyId);

    /**
     * RF-A2: Employees ordered by performance ratio (worst first).
     * performanceRatio = employee avg / global avg for same nodes.
     * Values > 1.0 indicate the employee is slower than average.
     */
    List<EmployeePerformanceResponse> getEmployeePerformance();

    /**
     * RF-A3: Throughput for a policy in the given period.
     * period values: "DAILY" | "WEEKLY" | "MONTHLY"
     */
    ThroughputResponse getThroughput(String policyId, String period);

    /**
     * RF-A4: SLA compliance per node for a policy.
     * Only nodes with "slaSeconds" defined in their metadata are included.
     */
    List<SlaComplianceResponse> getSlaCompliance(String policyId);

    /** RF-A5: Abandonment rate for a policy. */
    AbandonmentResponse getAbandonmentRate(String policyId);

    /** RF-A6: Consolidated dashboard for a policy. */
    AnalyticsDashboardResponse getDashboard(String policyId);
}
