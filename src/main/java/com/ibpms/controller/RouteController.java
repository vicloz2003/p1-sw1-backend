package com.ibpms.controller;

import com.ibpms.dto.response.RouteAdvisoryResponse;
import com.ibpms.service.api.RouteAdvisorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intelligent routing endpoint (RF-3.1): advisory "best route" recommendation for a decision
 * node, backed by the DL route predictor in ibpms_ml.
 *
 * <p>The whole feature is gated by {@code ibpms.routing.advisor.enabled} (default true). When
 * the flag is {@code false} this controller is not even registered, so the capability can be
 * switched off without touching any other code or the workflow engine.
 */
@RestController
@RequestMapping("/api/v1/route")
@ConditionalOnProperty(name = "ibpms.routing.advisor.enabled", havingValue = "true",
        matchIfMissing = true)
@PreAuthorize("hasAnyAuthority('ADMIN_DESIGNER', 'EMPLOYEE')")
public class RouteController {

    private final RouteAdvisorService routeAdvisorService;

    public RouteController(RouteAdvisorService routeAdvisorService) {
        this.routeAdvisorService = routeAdvisorService;
    }

    /**
     * Advisory best-branch recommendation for an instance's decision node.
     * @param nodeId optional decision node; defaults to the instance's current node.
     */
    @GetMapping("/advise/{instanceId}")
    public ResponseEntity<RouteAdvisoryResponse> advise(
            @PathVariable String instanceId,
            @RequestParam(required = false) String nodeId) {
        return ResponseEntity.ok(routeAdvisorService.adviseForInstance(instanceId, nodeId));
    }

    /** DL models' evaluation metrics (accuracy, AUC, detection rate) — for transparency/defense. */
    @GetMapping("/metrics")
    public ResponseEntity<Object> metrics() {
        return ResponseEntity.ok(routeAdvisorService.modelMetrics());
    }
}
