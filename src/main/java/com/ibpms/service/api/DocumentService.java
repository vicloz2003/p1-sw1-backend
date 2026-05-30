package com.ibpms.service.api;

import com.ibpms.dto.request.InitiateDocumentUploadRequest;
import com.ibpms.dto.request.InitiatePreProcessUploadRequest;
import com.ibpms.dto.response.AuditLogResponse;
import com.ibpms.dto.response.DocumentDownloadResponse;
import com.ibpms.dto.response.DocumentResponse;
import com.ibpms.dto.response.DocumentUploadInitiateResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Business logic for document lifecycle management (RF-02 to RF-10).
 */
public interface DocumentService {

    /**
     * Generates a presigned PUT URL and creates a PENDING_UPLOAD document record.
     * Called by CLIENT at process start or EMPLOYEE at an ACTION node (RF-02/03).
     */
    DocumentUploadInitiateResponse initiateUpload(InitiateDocumentUploadRequest request,
                                                   String userId,
                                                   String userRole);

    /**
     * Pre-process variant: generates a presigned PUT URL for a mandatory PROCESS_START
     * document before the process instance exists (RF-01).
     * The resulting record has {@code processInstanceId = null} until
     * {@code startProcess()} links it.
     */
    DocumentUploadInitiateResponse initiatePreProcessUpload(InitiatePreProcessUploadRequest request,
                                                             String userId,
                                                             String userRole);

    /**
     * Verifies the file exists in S3 (HeadObject) and marks the document CONFIRMED (RF-10).
     * Requires write permission on the document.
     */
    DocumentResponse confirmUpload(String documentId, String userId, String userRole);

    /**
     * Returns a presigned GET URL valid for 15 minutes and writes an audit log (RF-06).
     * Throws HTTP 403 if the caller has no read permission.
     */
    DocumentDownloadResponse download(String documentId,
                                       String userId,
                                       String userRole,
                                       HttpServletRequest httpRequest);

    /** Lists all non-deleted documents for a process instance (RF-04). */
    List<DocumentResponse> listByInstance(String processInstanceId);

    /** Lists all non-deleted documents of a client across all their trámites (RF-1.4). */
    List<DocumentResponse> listByClient(String clientId);

    /** Lists a client's non-deleted documents within a specific policy (RF-1.4). */
    List<DocumentResponse> listByPolicyAndClient(String policyId, String clientId);

    /**
     * Creates a new version of the document.
     * Moves the current S3 key to {@code versions[]} and generates a fresh presigned PUT URL.
     * Requires write permission (RF-07).
     */
    DocumentUploadInitiateResponse newVersion(String documentId,
                                               String fileName,
                                               String mimeType,
                                               String userId,
                                               String userRole);

    /** Soft-deletes the document (sets status = DELETED). Requires delete permission. */
    void delete(String documentId, String userId, String userRole, HttpServletRequest httpRequest);

    /** Full audit trail for a document (RF-08). ADMIN_DESIGNER only. */
    List<AuditLogResponse> getAuditLog(String documentId);
}
