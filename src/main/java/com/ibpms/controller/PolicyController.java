package com.ibpms.controller;

import com.ibpms.domain.DocumentRequirement;
import com.ibpms.dto.request.CreatePolicyRequest;
import com.ibpms.dto.request.UpdatePolicyRequest;
import com.ibpms.dto.response.PolicyResponse;
import com.ibpms.service.api.PolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN_DESIGNER', 'EMPLOYEE')")  // empleados leen la política para ver documentos requeridos en sus tareas
    public ResponseEntity<PolicyResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(policyService.getById(id));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    // ── Document Requirement endpoints (RF-01) ────────────────────────────────

    /** Add a document requirement to a policy. */
    @PostMapping("/{id}/document-requirements")
    public ResponseEntity<PolicyResponse> addDocumentRequirement(
            @PathVariable String id,
            @RequestBody DocumentRequirement requirement) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policyService.addDocumentRequirement(id, requirement));
    }

    /** Update a specific document requirement. */
    @PutMapping("/{id}/document-requirements/{reqId}")
    public ResponseEntity<PolicyResponse> updateDocumentRequirement(
            @PathVariable String id,
            @PathVariable String reqId,
            @RequestBody DocumentRequirement requirement) {
        return ResponseEntity.ok(policyService.updateDocumentRequirement(id, reqId, requirement));
    }

    /** Remove a document requirement (only allowed when policy has no ACTIVE instances). */
    @DeleteMapping("/{id}/document-requirements/{reqId}")
    public ResponseEntity<PolicyResponse> removeDocumentRequirement(
            @PathVariable String id,
            @PathVariable String reqId) {
        return ResponseEntity.ok(policyService.removeDocumentRequirement(id, reqId));
    }

    // ── NLP tags endpoint (RF-11) ─────────────────────────────────────────────

    /** Replace the semantic tags of a policy for NLP classification. */
    @PatchMapping("/{id}/tags")
    public ResponseEntity<PolicyResponse> updateTags(
            @PathVariable String id,
            @RequestBody List<String> tags) {
        return ResponseEntity.ok(policyService.updateTags(id, tags));
    }
}

