package com.ibpms.service.api;

import com.ibpms.dto.request.CreateBlankDocumentRequest;
import com.ibpms.dto.request.InitiateDocumentUploadRequest;
import com.ibpms.dto.request.InitiatePreProcessUploadRequest;
import com.ibpms.dto.request.UpdateDocumentPermissionsRequest;
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
                                                   String userRole,
                                                   String departmentId);

    /**
     * Pre-process variant: generates a presigned PUT URL for a mandatory PROCESS_START
     * document before the process instance exists (RF-01).
     * The resulting record has {@code processInstanceId = null} until
     * {@code startProcess()} links it.
     */
    DocumentUploadInitiateResponse initiatePreProcessUpload(InitiatePreProcessUploadRequest request,
                                                             String userId,
                                                             String userRole,
                                                             String departmentId);

    /**
     * Verifies the file exists in S3 (HeadObject) and marks the document CONFIRMED (RF-10).
     * Requires write permission on the document.
     */
    DocumentResponse confirmUpload(String documentId, String userId, String userRole, String departmentId);

    /**
     * Returns a presigned GET URL valid for 15 minutes and writes an audit log (RF-06).
     * Throws HTTP 403 if the caller has no read permission.
     */
    DocumentDownloadResponse download(String documentId,
                                       String userId,
                                       String userRole,
                                       String departmentId,
                                       HttpServletRequest httpRequest);

    /**
     * Returns a presigned GET URL (15 min) for a specific historical version of the
     * document and writes an audit log (RF-07 version control). Requires read permission.
     */
    DocumentDownloadResponse downloadVersion(String documentId,
                                             String versionId,
                                             String userId,
                                             String userRole,
                                             String departmentId,
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
                                               String userRole,
                                               String departmentId);

    /** Soft-deletes the document (sets status = DELETED). Requires delete permission. */
    void delete(String documentId, String userId, String userRole, String departmentId, HttpServletRequest httpRequest);

    /** Full audit trail for a document (RF-08). ADMIN_DESIGNER only. */
    List<AuditLogResponse> getAuditLog(String documentId);

    /**
     * RF-1.10: a functionary creates a blank Office document (Word/Excel/PowerPoint) at the
     * node they are working. The new document is owned by the functionary's department
     * (department-scoped ACL) so the whole department can co-edit it. Returns the created doc.
     */
    DocumentResponse createBlankDocument(CreateBlankDocumentRequest request,
                                         String userId,
                                         String userRole,
                                         String departmentId);

    /**
     * RF-1.10 discovery: non-deleted documents the caller's department may access
     * (its {@code departmentId} appears in canRead/canWrite) and whose trámite is active.
     * This is the functionary's "my department documents" inbox.
     */
    List<DocumentResponse> listByDepartment(String departmentId);

    /**
     * Reassigns the document ACL (RF-1.5 / RF-1.9) and records a PERMISSION_CHANGE
     * audit entry. ADMIN_DESIGNER only. A {@code null} list leaves that permission
     * unchanged.
     */
    DocumentResponse updatePermissions(String documentId,
                                       UpdateDocumentPermissionsRequest request,
                                       String userId,
                                       String userRole);
}
