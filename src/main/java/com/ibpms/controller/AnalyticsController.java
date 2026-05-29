package com.ibpms.controller;

import com.ibpms.dto.response.*;
import com.ibpms.service.api.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** RF-A1: Bottlenecks globally */
    @GetMapping("/bottlenecks")
    public ResponseEntity<List<BottleneckResponse>> getBottlenecks() {
        return ResponseEntity.ok(analyticsService.getBottlenecks());
    }

    /** RF-A1 (scoped): Bottlenecks for a specific policy */
    @GetMapping("/bottlenecks/{policyId}")
    public ResponseEntity<List<BottleneckResponse>> getBottlenecksByPolicy(@PathVariable String policyId) {
        return ResponseEntity.ok(analyticsService.getBottlenecksByPolicy(policyId));
    }

    /**
     * RF-A2: Employee performance — sorted worst-first.
     * performanceRatio > 1.0 means slower than the average for the same nodes.
     */
    @GetMapping("/employee-performance")
    public ResponseEntity<List<EmployeePerformanceResponse>> getEmployeePerformance() {
        return ResponseEntity.ok(analyticsService.getEmployeePerformance());
    }

    /**
     * RF-A3: Throughput for a policy.
     * @param period DAILY | WEEKLY | MONTHLY (default: MONTHLY)
     */
    @GetMapping("/throughput/{policyId}")
    public ResponseEntity<ThroughputResponse> getThroughput(
            @PathVariable String policyId,
            @RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(analyticsService.getThroughput(policyId, period));
    }

    /**
     * RF-A4: SLA compliance per node for a policy.
     * Only ACTION nodes with slaSeconds defined in metadata are included.
     */
    @GetMapping("/sla/{policyId}")
    public ResponseEntity<List<SlaComplianceResponse>> getSlaCompliance(@PathVariable String policyId) {
        return ResponseEntity.ok(analyticsService.getSlaCompliance(policyId));
    }

    /** RF-A5: Abandonment rate for a policy */
    @GetMapping("/abandonment/{policyId}")
    public ResponseEntity<AbandonmentResponse> getAbandonmentRate(@PathVariable String policyId) {
        return ResponseEntity.ok(analyticsService.getAbandonmentRate(policyId));
    }

    /** RF-A6: Consolidated KPI dashboard for a policy */
    @GetMapping("/dashboard/{policyId}")
    public ResponseEntity<AnalyticsDashboardResponse> getDashboard(@PathVariable String policyId) {
        return ResponseEntity.ok(analyticsService.getDashboard(policyId));
    }
}
