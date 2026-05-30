package com.ibpms.controller;

import com.ibpms.dto.response.PolicyRiskResponse;
import com.ibpms.service.api.RiskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intelligent risk engine endpoints (RF-3). For the policy manager (ADMIN_DESIGNER): scores
 * active instances for delay risk, anomalies and resource-allocation priority via ibpms_ml.
 */
@RestController
@RequestMapping("/api/v1/risk")
@PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    /** RF-3.2/3.3/3.4: risk report of all ACTIVE instances of a policy, highest-risk first. */
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<PolicyRiskResponse> assessPolicy(@PathVariable String policyId) {
        return ResponseEntity.ok(riskService.assessPolicy(policyId));
    }
}
