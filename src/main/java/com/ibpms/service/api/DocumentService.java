package com.ibpms.service.api;

import com.ibpms.dto.request.InitiateDocumentUploadRequest;
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
     * Verifies the file exists in S3 (HeadObject) and marks the document CONFIRMED (RF-10).
     */
    DocumentResponse confirmUpload(String documentId, String userId);

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
