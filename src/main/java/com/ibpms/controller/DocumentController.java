package com.ibpms.controller;

import com.ibpms.dto.request.InitiateDocumentUploadRequest;
import com.ibpms.dto.request.InitiatePreProcessUploadRequest;
import com.ibpms.dto.response.AuditLogResponse;
import com.ibpms.dto.response.DocumentDownloadResponse;
import com.ibpms.dto.response.DocumentResponse;
import com.ibpms.dto.response.DocumentUploadInitiateResponse;
import com.ibpms.service.api.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for document lifecycle management (RF-02 to RF-10).
 *
 * <p>Base path: {@code /api/v1/documents}
 * Documents associated with a specific process instance are also accessible
 * via {@code /api/v1/processes/{instanceId}/documents}.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * RF-01: Pre-process upload — CLIENT uploads a mandatory PROCESS_START document
     * before the process instance is created. Returns a presigned PUT URL.
     * The document is linked to the instance when {@code POST /processes} is called.
     */
    @PostMapping("/pre-process")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<DocumentUploadInitiateResponse> initiatePreProcessUpload(
            @Valid @RequestBody InitiatePreProcessUploadRequest request,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.initiatePreProcessUpload(request, userId, userRole));
    }

    /**
     * RF-02 / RF-03: Request a presigned S3 PUT URL and create a PENDING_UPLOAD record.
     * CLIENT calls this at process start; EMPLOYEE calls this when completing an ACTION node.
     */
    @PostMapping("/initiate")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<DocumentUploadInitiateResponse> initiateUpload(
            @Valid @RequestBody InitiateDocumentUploadRequest request,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.initiateUpload(request, userId, userRole));
    }

    /**
     * RF-10: Confirm that the file has been uploaded to S3 (HeadObject verification).
     * Marks the document CONFIRMED.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<DocumentResponse> confirmUpload(
            @PathVariable String id,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(documentService.confirmUpload(id, userId, userRole));
    }

    /**
     * RF-06: Generate a presigned S3 GET URL (15 min) for downloading.
     * Requires read permission. Writes audit log.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<DocumentDownloadResponse> download(
            @PathVariable String id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(documentService.download(id, userId, userRole, httpRequest));
    }

    /**
     * RF-07: Upload a new version of an existing document.
     * Returns a fresh presigned PUT URL. Requires write permission.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<DocumentUploadInitiateResponse> newVersion(
            @PathVariable String id,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String mimeType,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(documentService.newVersion(id, fileName, mimeType, userId, userRole));
    }

    /**
     * Soft-deletes a document (status = DELETED). The S3 object is NOT removed.
     * Requires delete permission.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        documentService.delete(id, userId, userRole, httpRequest);
        return ResponseEntity.noContent().build();
    }

    /**
     * RF-08: Full audit trail for a document. ADMIN_DESIGNER only.
     */
    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
    public ResponseEntity<List<AuditLogResponse>> getAuditLog(@PathVariable String id) {
        return ResponseEntity.ok(documentService.getAuditLog(id));
    }

    /**
     * RF-1.4: All documents of a client across all their trámites (per-client repository).
     * Used by the jefe de negocio. ADMIN_DESIGNER only.
     */
    @GetMapping("/by-client/{clientId}")
    @PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
    public ResponseEntity<List<DocumentResponse>> getByClient(@PathVariable String clientId) {
        return ResponseEntity.ok(documentService.listByClient(clientId));
    }

    /**
     * RF-1.4: A client's documents within a specific policy (per policy AND per client).
     * ADMIN_DESIGNER only.
     */
    @GetMapping("/by-policy/{policyId}/client/{clientId}")
    @PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
    public ResponseEntity<List<DocumentResponse>> getByPolicyAndClient(
            @PathVariable String policyId,
            @PathVariable String clientId) {
        return ResponseEntity.ok(documentService.listByPolicyAndClient(policyId, clientId));
    }
}
