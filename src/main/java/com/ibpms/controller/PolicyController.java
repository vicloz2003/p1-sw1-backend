package com.ibpms.controller;

import com.ibpms.dto.request.CreatePolicyRequest;
import com.ibpms.dto.request.UpdatePolicyRequest;
import com.ibpms.dto.response.PolicyResponse;
import com.ibpms.service.api.PolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
@PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('ADMIN_DESIGNER', 'EMPLOYEE')")
    public ResponseEntity<List<PolicyResponse>> getActive() {
        return ResponseEntity.ok(policyService.getActive());
    }

    @GetMapping
    public ResponseEntity<List<PolicyResponse>> getAll() {
        return ResponseEntity.ok(policyService.getAll());
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody CreatePolicyRequest request,
                                                  Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(request, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PolicyResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody UpdatePolicyRequest request) {
        return ResponseEntity.ok(policyService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<PolicyResponse> publish(@PathVariable String id) {
        return ResponseEntity.ok(policyService.publish(id));
    }
}

