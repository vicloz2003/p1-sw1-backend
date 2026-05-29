package com.ibpms.service.impl;

import com.ibpms.domain.DocumentAuditLog;
import com.ibpms.domain.DocumentPermissions;
import com.ibpms.domain.DocumentVersion;
import com.ibpms.domain.ProcessDocument;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.DocumentAction;
import com.ibpms.domain.enums.DocumentStatus;
import com.ibpms.dto.request.InitiateDocumentUploadRequest;
import com.ibpms.dto.response.AuditLogResponse;
import com.ibpms.dto.response.DocumentDownloadResponse;
import com.ibpms.dto.response.DocumentResponse;
import com.ibpms.dto.response.DocumentUploadInitiateResponse;
import com.ibpms.exception.DocumentAccessDeniedException;
import com.ibpms.exception.DocumentNotFoundException;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.repository.DocumentAuditLogRepository;
import com.ibpms.repository.ProcessDocumentRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private final ProcessDocumentRepository documentRepository;
    private final DocumentAuditLogRepository auditLogRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final S3Service s3Service;

    public DocumentServiceImpl(ProcessDocumentRepository documentRepository,
                                DocumentAuditLogRepository auditLogRepository,
                                ProcessInstanceRepository instanceRepository,
                                S3Service s3Service) {
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.instanceRepository = instanceRepository;
        this.s3Service = s3Service;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-02 / RF-03: Initiate upload
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentUploadInitiateResponse initiateUpload(InitiateDocumentUploadRequest request,
                                                          String userId,
                                                          String userRole) {
        ProcessInstance instance = instanceRepository.findById(request.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Process instance not found: " + request.processInstanceId()));

        String requirementId = request.documentRequirementId() != null
                ? request.documentRequirementId()
                : "adhoc";

        Map<String, String> s3Result = s3Service.initiateDocumentUpload(
                instance.getBusinessPolicyId(),
                instance.getId(),
                requirementId,
                request.fileName(),
                request.mimeType()
        );

        String s3Key = s3Result.get("key");
        String presignedUrl = s3Result.get("presignedUrl");

        // Build default permissions: uploader can read/write/delete; ADMIN_DESIGNER always has access
        DocumentPermissions permissions = new DocumentPermissions(
                List.of(userId, "ADMIN_DESIGNER"),
                List.of(userId, "ADMIN_DESIGNER"),
                List.of(userId, "ADMIN_DESIGNER")
        );

        ProcessDocument doc = new ProcessDocument();
        doc.setProcessInstanceId(instance.getId());
        doc.setBusinessPolicyId(instance.getBusinessPolicyId());
        doc.setDocumentRequirementId(request.documentRequirementId());
        doc.setFileName(request.fileName());
        doc.setMimeType(request.mimeType());
        doc.setS3Key(s3Key);
        doc.setUploadedBy(userId);
        doc.setUploadedByRole(userRole);
        doc.setStatus(DocumentStatus.PENDING_UPLOAD);
        doc.setVersions(new ArrayList<>());
        doc.setPermissions(permissions);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setTaskId(request.taskId());

        ProcessDocument saved = documentRepository.save(doc);

        // Audit: UPLOAD initiated (final confirmation audit happens in confirmUpload)
        saveAuditLog(saved.getId(), instance.getId(), userId, userRole,
                DocumentAction.UPLOAD, null, "PENDING_UPLOAD initiated");

        return new DocumentUploadInitiateResponse(saved.getId(), s3Key, presignedUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-10: Confirm upload
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentResponse confirmUpload(String documentId, String userId) {
        ProcessDocument doc = findDoc(documentId);

        if (doc.getStatus() == DocumentStatus.CONFIRMED) {
            return toResponse(doc); // idempotent
        }

        if (!s3Service.objectExists(doc.getS3Key())) {
            throw new IllegalStateException(
                    "File has not been uploaded to S3 yet for document: " + documentId);
        }

        doc.setStatus(DocumentStatus.CONFIRMED);
        doc.setConfirmedAt(LocalDateTime.now());
        ProcessDocument saved = documentRepository.save(doc);

        saveAuditLog(saved.getId(), saved.getProcessInstanceId(), userId, null,
                DocumentAction.UPLOAD, null, "CONFIRMED after S3 HeadObject verification");

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-06: Download (presigned GET URL + audit)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentDownloadResponse download(String documentId,
                                              String userId,
                                              String userRole,
                                              HttpServletRequest httpRequest) {
        ProcessDocument doc = findDoc(documentId);

        if (!canRead(doc, userId, userRole)) {
            saveAuditLog(doc.getId(), doc.getProcessInstanceId(), userId, userRole,
                    DocumentAction.PERMISSION_CHECK_FAILED,
                    extractIp(httpRequest),
                    "Read access denied");
            throw new DocumentAccessDeniedException(
                    "You do not have read access to document: " + documentId);
        }

        Duration expiry = Duration.ofMinutes(15);
        String presignedUrl = s3Service.generatePresignedGetUrl(doc.getS3Key(), expiry);
        LocalDateTime expiresAt = LocalDateTime.now().plus(expiry);

        saveAuditLog(doc.getId(), doc.getProcessInstanceId(), userId, userRole,
                DocumentAction.DOWNLOAD, extractIp(httpRequest), "Presigned GET URL issued");

        return new DocumentDownloadResponse(doc.getId(), doc.getFileName(), presignedUrl, expiresAt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-04: List documents by instance
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<DocumentResponse> listByInstance(String processInstanceId) {
        return documentRepository
                .findByProcessInstanceIdAndStatusNot(processInstanceId, DocumentStatus.DELETED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-07: New version
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentUploadInitiateResponse newVersion(String documentId,
                                                      String fileName,
                                                      String mimeType,
                                                      String userId,
                                                      String userRole) {
        ProcessDocument doc = findDoc(documentId);

        if (!canWrite(doc, userId, userRole)) {
            throw new DocumentAccessDeniedException(
                    "You do not have write access to document: " + documentId);
        }

        // Archive current version
        if (doc.getS3Key() != null) {
            DocumentVersion version = new DocumentVersion(
                    UUID.randomUUID().toString(),
                    doc.getS3Key(),
                    doc.getUploadedBy(),
                    doc.getUploadedAt(),
                    null
            );
            if (doc.getVersions() == null) doc.setVersions(new ArrayList<>());
            doc.getVersions().add(version);
        }

        // Generate new S3 key and presigned URL
        Map<String, String> s3Result = s3Service.initiateDocumentUpload(
                doc.getBusinessPolicyId(),
                doc.getProcessInstanceId(),
                doc.getDocumentRequirementId() != null ? doc.getDocumentRequirementId() : "adhoc",
                fileName != null ? fileName : doc.getFileName(),
                mimeType != null ? mimeType : doc.getMimeType()
        );

        doc.setS3Key(s3Result.get("key"));
        doc.setFileName(fileName != null ? fileName : doc.getFileName());
        doc.setMimeType(mimeType != null ? mimeType : doc.getMimeType());
        doc.setStatus(DocumentStatus.PENDING_UPLOAD);
        doc.setUploadedBy(userId);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setConfirmedAt(null);
        documentRepository.save(doc);

        saveAuditLog(doc.getId(), doc.getProcessInstanceId(), userId, userRole,
                DocumentAction.REPLACE, null, "New version initiated");

        return new DocumentUploadInitiateResponse(doc.getId(), doc.getS3Key(), s3Result.get("presignedUrl"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Soft delete
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void delete(String documentId,
                       String userId,
                       String userRole,
                       HttpServletRequest httpRequest) {
        ProcessDocument doc = findDoc(documentId);

        if (!canDelete(doc, userId, userRole)) {
            saveAuditLog(doc.getId(), doc.getProcessInstanceId(), userId, userRole,
                    DocumentAction.PERMISSION_CHECK_FAILED,
                    extractIp(httpRequest), "Delete access denied");
            throw new DocumentAccessDeniedException(
                    "You do not have delete access to document: " + documentId);
        }

        doc.setStatus(DocumentStatus.DELETED);
        documentRepository.save(doc);

        saveAuditLog(doc.getId(), doc.getProcessInstanceId(), userId, userRole,
                DocumentAction.DELETE, extractIp(httpRequest), "Soft-deleted");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-08: Audit log
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<AuditLogResponse> getAuditLog(String documentId) {
        return auditLogRepository.findByDocumentIdOrderByTimestampAsc(documentId)
                .stream()
                .map(this::toAuditResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ProcessDocument findDoc(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private boolean canRead(ProcessDocument doc, String userId, String userRole) {
        return hasPermission(doc.getPermissions() != null
                ? doc.getPermissions().getCanRead() : null, userId, userRole);
    }

    private boolean canWrite(ProcessDocument doc, String userId, String userRole) {
        return hasPermission(doc.getPermissions() != null
                ? doc.getPermissions().getCanWrite() : null, userId, userRole);
    }

    private boolean canDelete(ProcessDocument doc, String userId, String userRole) {
        return hasPermission(doc.getPermissions() != null
                ? doc.getPermissions().getCanDelete() : null, userId, userRole);
    }

    /**
     * Returns true if the caller's userId OR role appears in the permission list.
     * ADMIN_DESIGNER always has access.
     */
    private boolean hasPermission(List<String> allowed, String userId, String userRole) {
        if ("ADMIN_DESIGNER".equals(userRole)) return true;
        if (allowed == null || allowed.isEmpty()) return false;
        return allowed.contains(userId) || allowed.contains(userRole);
    }

    @Async
    void saveAuditLog(String documentId, String processInstanceId,
                      String userId, String userRole,
                      DocumentAction action, String ipAddress, String detail) {
        DocumentAuditLog log = new DocumentAuditLog();
        log.setDocumentId(documentId);
        log.setProcessInstanceId(processInstanceId);
        log.setUserId(userId);
        log.setUserRole(userRole);
        log.setAction(action);
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    private DocumentResponse toResponse(ProcessDocument doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getProcessInstanceId(),
                doc.getBusinessPolicyId(),
                doc.getDocumentRequirementId(),
                doc.getFileName(),
                doc.getMimeType(),
                doc.getUploadedBy(),
                doc.getUploadedByRole(),
                doc.getStatus(),
                doc.getPermissions(),
                doc.getVersions(),
                doc.getUploadedAt(),
                doc.getConfirmedAt(),
                doc.getTaskId()
        );
    }

    private AuditLogResponse toAuditResponse(DocumentAuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getDocumentId(),
                log.getProcessInstanceId(),
                log.getUserId(),
                log.getUserRole(),
                log.getAction(),
                log.getTimestamp(),
                log.getIpAddress(),
                log.getDetail()
        );
    }
}
