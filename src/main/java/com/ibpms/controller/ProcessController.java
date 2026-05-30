package com.ibpms.controller;

import com.ibpms.dto.request.StartProcessRequest;
import com.ibpms.dto.response.DocumentResponse;
import com.ibpms.dto.response.ProcessStatusResponse;
import com.ibpms.service.api.DocumentService;
import com.ibpms.service.api.ProcessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/processes")
public class ProcessController {

    private final ProcessService processService;
    private final DocumentService documentService;

    public ProcessController(ProcessService processService, DocumentService documentService) {
        this.processService = processService;
        this.documentService = documentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
    public ResponseEntity<List<ProcessStatusResponse>> getAll() {
        return ResponseEntity.ok(processService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('EMPLOYEE', 'CLIENT')")
    public ResponseEntity<ProcessStatusResponse> start(@Valid @RequestBody StartProcessRequest request,
                                                        Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(processService.startProcess(request, userId));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ProcessStatusResponse> getStatus(@PathVariable String id) {
        return ResponseEntity.ok(processService.getStatus(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('CLIENT')")
    public ResponseEntity<List<ProcessStatusResponse>> getMyProcesses(
            Authentication authentication) {
        String clientId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(processService.getByClientId(clientId));
    }

    /**
     * RF-04: List all (non-deleted) documents for a specific process instance.
     * Accessible by the owner client, any employee, and the admin.
     */
    @GetMapping("/{instanceId}/documents")
    public ResponseEntity<List<DocumentResponse>> getDocuments(@PathVariable String instanceId) {
        return ResponseEntity.ok(documentService.listByInstance(instanceId));
    }
}

