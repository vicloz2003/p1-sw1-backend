package com.ibpms.service.impl;

import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.DocumentAuditLog;
import com.ibpms.domain.DocumentPermissions;
import com.ibpms.domain.DocumentRequirement;
import com.ibpms.domain.DocumentVersion;
import com.ibpms.domain.ProcessDocument;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.DocumentAction;
import com.ibpms.domain.enums.DocumentStatus;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.dto.request.CreateBlankDocumentRequest;
import com.ibpms.dto.request.InitiateDocumentUploadRequest;
import com.ibpms.dto.request.InitiatePreProcessUploadRequest;
import com.ibpms.dto.request.UpdateDocumentPermissionsRequest;
import com.ibpms.dto.response.AuditLogResponse;
import com.ibpms.dto.response.DocumentDownloadResponse;
import com.ibpms.dto.response.DocumentResponse;
import com.ibpms.dto.response.DocumentUploadInitiateResponse;
import com.ibpms.exception.DocumentAccessDeniedException;
import com.ibpms.exception.DocumentNotFoundException;
import com.ibpms.exception.DocumentUploadValidationException;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.domain.User;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.DocumentAuditLogRepository;
import com.ibpms.repository.ProcessDocumentRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.api.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String ROLE_ADMIN = "ADMIN_DESIGNER";

    private final ProcessDocumentRepository documentRepository;
    private final DocumentAuditLogRepository auditLogRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final BusinessPolicyRepository policyRepository;
    private final ActivityTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    public DocumentServiceImpl(ProcessDocumentRepository documentRepository,
                                DocumentAuditLogRepository auditLogRepository,
                                ProcessInstanceRepository instanceRepository,
                                BusinessPolicyRepository policyRepository,
                                ActivityTaskRepository taskRepository,
                                UserRepository userRepository,
                                S3Service s3Service) {
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.instanceRepository = instanceRepository;
        this.policyRepository = policyRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-02 / RF-03: Initiate upload
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentUploadInitiateResponse initiateUpload(InitiateDocumentUploadRequest request,
                                                          String userId,
                                                          String userRole,
                                                          String departmentId) {
        ProcessInstance instance = instanceRepository.findById(request.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Process instance not found: " + request.processInstanceId()));

        String requirementId = request.documentRequirementId() != null
                ? request.documentRequirementId()
                : "adhoc";

        BusinessPolicy policy = policyRepository.findById(instance.getBusinessPolicyId()).orElse(null);
        // Governance of the upload (RF-1.7): uploader role + allowed MIME types.
        validateUpload(findRequirement(policy, request.documentRequirementId()), userRole, request.mimeType());

        Map<String, String> s3Result = s3Service.initiateDocumentUpload(
                instance.getBusinessPolicyId(),
                instance.getClientId(),
                instance.getId(),
                requirementId,
                request.fileName(),
                request.mimeType()
        );

        String s3Key = s3Result.get("key");
        String presignedUrl = s3Result.get("presignedUrl");

        // Context-derived ACL (RF-1.5/1.9): no blanket EMPLOYEE. A client's upload is owned by the
        // client and readable by participating departments; a functionary's upload is owned by their
        // department (department-scoped co-edit).
        boolean clientUpload = "CLIENT".equals(userRole);
        DocumentPermissions permissions = deriveAcl(
                clientUpload, userId, departmentId, instance.getClientId(), policyDepartments(policy));

        ProcessDocument doc = new ProcessDocument();
        doc.setProcessInstanceId(instance.getId());
        doc.setBusinessPolicyId(instance.getBusinessPolicyId());
        doc.setClientId(instance.getClientId());
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
    // RF-01: Pre-process upload (before instance exists)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentUploadInitiateResponse initiatePreProcessUpload(InitiatePreProcessUploadRequest request,
                                                                    String userId,
                                                                    String userRole,
                                                                    String departmentId) {
        // The CLIENT uploading their own docs via the agent → clientId = their userId.
        // An EMPLOYEE on behalf of a client provides clientId explicitly in the request.
        String clientId = (request.clientId() != null && !request.clientId().isBlank())
                ? request.clientId()
                : userId;

        BusinessPolicy policy = policyRepository.findById(request.policyId()).orElse(null);
        // Governance of the upload (RF-1.7/1.8): uploader role + allowed MIME types.
        validateUpload(findRequirement(policy, request.documentRequirementId()), userRole, request.mimeType());

        Map<String, String> s3Result = s3Service.initiateDocumentUpload(
                request.policyId(),
                clientId,
                "pre_process",
                request.documentRequirementId(),
                request.fileName(),
                request.mimeType()
        );

        String s3Key = s3Result.get("key");
        String presignedUrl = s3Result.get("presignedUrl");

        // Pre-process documents belong to the client; participating departments may read them
        // when they later work the trámite (RF-1.5).
        DocumentPermissions permissions = deriveAcl(
                true, userId, departmentId, clientId, policyDepartments(policy));

        ProcessDocument doc = new ProcessDocument();
        doc.setProcessInstanceId(null);
        doc.setBusinessPolicyId(request.policyId());
        doc.setClientId(clientId);
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

        ProcessDocument saved = documentRepository.save(doc);

        saveAuditLog(saved.getId(), null, userId, userRole,
                DocumentAction.UPLOAD, null, "Pre-process PENDING_UPLOAD initiated");

        return new DocumentUploadInitiateResponse(saved.getId(), s3Key, presignedUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-10: Confirm upload
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentResponse confirmUpload(String documentId, String userId, String userRole, String departmentId) {
        ProcessDocument doc = findDoc(documentId);

        if (!canWrite(doc, userId, userRole, departmentId)) {
            throw new DocumentAccessDeniedException(
                    "You do not have write access to document: " + documentId);
        }

        if (doc.getStatus() == DocumentStatus.CONFIRMED) {
            return toResponse(doc); // idempotent
        }

        if (!s3Service.objectExists(doc.getS3Key())) {
            throw new IllegalStateException(
                    "File has not been uploaded to S3 yet for document: " + documentId);
        }

        // Governance (RF-1.7): enforce the requirement's max size now that the object exists.
        DocumentRequirement req = doc.getDocumentRequirementId() != null
                ? findRequirement(policyRepository.findById(doc.getBusinessPolicyId()).orElse(null),
                                  doc.getDocumentRequirementId())
                : null;
        if (req != null && req.getMaxSizeBytes() != null) {
            long size = s3Service.getObjectSize(doc.getS3Key());
            if (size > req.getMaxSizeBytes()) {
                throw new DocumentUploadValidationException(
                        "El archivo (" + size + " bytes) excede el máximo permitido para '"
                                + req.getName() + "' (" + req.getMaxSizeBytes() + " bytes).");
            }
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
                                              String departmentId,
                                              HttpServletRequest httpRequest) {
        ProcessDocument doc = findDoc(documentId);

        if (!canRead(doc, userId, userRole, departmentId)) {
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

    @Override
    public List<DocumentResponse> listByClient(String clientId) {
        return documentRepository
                .findByClientIdAndStatusNot(clientId, DocumentStatus.DELETED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<DocumentResponse> listByPolicyAndClient(String policyId, String clientId) {
        return documentRepository
                .findByBusinessPolicyIdAndClientIdAndStatusNot(policyId, clientId, DocumentStatus.DELETED)
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
                                                      String userRole,
                                                      String departmentId) {
        ProcessDocument doc = findDoc(documentId);

        if (!canWrite(doc, userId, userRole, departmentId)) {
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
                doc.getClientId(),
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
                       String departmentId,
                       HttpServletRequest httpRequest) {
        ProcessDocument doc = findDoc(documentId);

        if (!canDelete(doc, userId, userRole, departmentId)) {
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
        List<DocumentAuditLog> logs = auditLogRepository.findByDocumentIdOrderByTimestampAsc(documentId);
        // Resolve userId → username in one batch query
        Set<String> userIds = logs.stream()
                .map(DocumentAuditLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> usernameById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return logs.stream()
                .map(log -> toAuditResponse(log, usernameById.get(log.getUserId())))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-1.10: functionary creates a blank Office document + department inbox
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentResponse createBlankDocument(CreateBlankDocumentRequest request,
                                                String userId,
                                                String userRole,
                                                String departmentId) {
        ProcessInstance instance = instanceRepository.findById(request.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Process instance not found: " + request.processInstanceId()));

        // Authorization: the functionary may add documents at a node only if they are working it —
        // there must be a task at this instance/node assigned to their department (or to them).
        if (!ROLE_ADMIN.equals(userRole) && !authorizedAtNode(request, userId, departmentId)) {
            throw new DocumentAccessDeniedException(
                    "No estás autorizado para crear documentos en este nodo.");
        }

        String kind = request.kind() == null ? "WORD" : request.kind().toUpperCase();
        String ext;
        String mime;
        String template;
        switch (kind) {
            case "CELL", "EXCEL", "XLSX" -> {
                ext = "xlsx";
                mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                template = "onlyoffice-templates/blank.xlsx";
            }
            default -> {
                ext = "docx";
                mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                template = "onlyoffice-templates/blank.docx";
            }
        }
        String fileName = ensureExtension(request.fileName(), ext);
        byte[] content = readTemplate(template);

        String nodeSegment = request.nodeId() != null && !request.nodeId().isBlank()
                ? request.nodeId() : "generated";
        Map<String, String> s3Result = s3Service.initiateDocumentUpload(
                instance.getBusinessPolicyId(), instance.getClientId(), instance.getId(),
                nodeSegment, fileName, mime);
        String s3Key = s3Result.get("key");
        s3Service.putObject(s3Key, content, mime);

        BusinessPolicy policy = policyRepository.findById(instance.getBusinessPolicyId()).orElse(null);
        // Department-scoped ACL: the creating department co-edits (RF-1.9).
        DocumentPermissions permissions = deriveAcl(
                false, userId, departmentId, instance.getClientId(), policyDepartments(policy));

        ProcessDocument doc = new ProcessDocument();
        doc.setProcessInstanceId(instance.getId());
        doc.setBusinessPolicyId(instance.getBusinessPolicyId());
        doc.setClientId(instance.getClientId());
        doc.setDocumentRequirementId(null);
        doc.setFileName(fileName);
        doc.setMimeType(mime);
        doc.setS3Key(s3Key);
        doc.setUploadedBy(userId);
        doc.setUploadedByRole(userRole);
        doc.setStatus(DocumentStatus.CONFIRMED);
        doc.setVersions(new ArrayList<>());
        doc.setPermissions(permissions);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setConfirmedAt(LocalDateTime.now());
        doc.setTaskId(request.taskId());
        ProcessDocument saved = documentRepository.save(doc);

        saveAuditLog(saved.getId(), instance.getId(), userId, userRole, DocumentAction.UPLOAD, null,
                "Documento en blanco creado por el funcionario (" + kind + ")");
        return toResponse(saved);
    }

    @Override
    public List<DocumentResponse> listByDepartment(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) return List.of();

        // The inbox is for co-editing: show only documents this department may WRITE
        // (the ones it must elaborate). Read-only participation is reviewed within the task.
        List<ProcessDocument> deptDocs = documentRepository.findByStatusNot(DocumentStatus.DELETED).stream()
                .filter(d -> writableByDepartment(d, departmentId))
                .toList();

        // Only documents of trámites that are still ACTIVE (the work the department must do now).
        Set<String> activeInstanceIds = deptDocs.stream()
                .map(ProcessDocument::getProcessInstanceId)
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> instanceRepository.findById(id)
                        .map(i -> i.getStatus() == InstanceStatus.ACTIVE)
                        .orElse(false))
                .collect(Collectors.toSet());

        return deptDocs.stream()
                .filter(d -> d.getProcessInstanceId() != null
                        && activeInstanceIds.contains(d.getProcessInstanceId()))
                .map(this::toResponse)
                .toList();
    }

    private boolean authorizedAtNode(CreateBlankDocumentRequest request, String userId, String departmentId) {
        return taskRepository.findByProcessInstanceId(request.processInstanceId()).stream()
                .anyMatch(t -> (request.nodeId() == null || request.nodeId().isBlank()
                                || request.nodeId().equals(t.getNodeId()))
                        && ((departmentId != null && departmentId.equals(t.getAssignedDepartmentId()))
                            || userId.equals(t.getAssignedUserId())));
    }

    private boolean writableByDepartment(ProcessDocument doc, String departmentId) {
        DocumentPermissions p = doc.getPermissions();
        return p != null && p.getCanWrite() != null && p.getCanWrite().contains(departmentId);
    }

    private String ensureExtension(String fileName, String ext) {
        String name = (fileName == null || fileName.isBlank()) ? "documento" : fileName.trim();
        return name.toLowerCase().endsWith("." + ext) ? name : name + "." + ext;
    }

    private byte[] readTemplate(String classpath) {
        try {
            return new ClassPathResource(classpath).getContentAsByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la plantilla: " + classpath, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-1.5 / RF-1.9: Reassign document ACL
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentResponse updatePermissions(String documentId,
                                              UpdateDocumentPermissionsRequest request,
                                              String userId,
                                              String userRole) {
        ProcessDocument doc = findDoc(documentId);

        DocumentPermissions current = doc.getPermissions() != null
                ? doc.getPermissions()
                : new DocumentPermissions(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        // A null list leaves that permission unchanged; a provided list replaces it.
        if (request.canRead() != null)   current.setCanRead(new ArrayList<>(request.canRead()));
        if (request.canWrite() != null)  current.setCanWrite(new ArrayList<>(request.canWrite()));
        if (request.canDelete() != null) current.setCanDelete(new ArrayList<>(request.canDelete()));

        doc.setPermissions(current);
        ProcessDocument saved = documentRepository.save(doc);

        saveAuditLog(saved.getId(), saved.getProcessInstanceId(), userId, userRole,
                DocumentAction.PERMISSION_CHANGE, null,
                "ACL updated — read=" + current.getCanRead()
                        + " write=" + current.getCanWrite()
                        + " delete=" + current.getCanDelete());

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ProcessDocument findDoc(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** departmentIds of the partitions (lanes) that participate in the policy. */
    private Set<String> policyDepartments(BusinessPolicy policy) {
        Set<String> depts = new LinkedHashSet<>();
        if (policy != null && policy.getPartitions() != null) {
            policy.getPartitions().forEach(p -> {
                if (p.getDepartmentId() != null && !p.getDepartmentId().isBlank()) depts.add(p.getDepartmentId());
            });
        }
        return depts;
    }

    private DocumentRequirement findRequirement(BusinessPolicy policy, String requirementId) {
        if (policy == null || requirementId == null || policy.getDocumentRequirements() == null) return null;
        return policy.getDocumentRequirements().stream()
                .filter(r -> requirementId.equals(r.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Enforces the requirement's governance (RF-1.7): who may upload and which MIME types.
     * Size is checked at confirm time (the bytes do not exist yet here). No requirement = ad-hoc, allowed.
     */
    private void validateUpload(DocumentRequirement req, String userRole, String mimeType) {
        if (req == null) return;
        String allowedRole = req.getUploaderRole();
        if (allowedRole != null && !allowedRole.isBlank()
                && !"ANY".equalsIgnoreCase(allowedRole)
                && !allowedRole.equalsIgnoreCase(userRole)) {
            throw new DocumentUploadValidationException(
                    "El rol '" + userRole + "' no puede subir '" + req.getName()
                            + "' (requiere " + allowedRole + ").");
        }
        List<String> allowedMimes = req.getAllowedMimeTypes();
        if (allowedMimes != null && !allowedMimes.isEmpty()
                && mimeType != null && !allowedMimes.contains(mimeType)) {
            throw new DocumentUploadValidationException(
                    "El tipo '" + mimeType + "' no está permitido para '" + req.getName()
                            + "'. Permitidos: " + allowedMimes + ".");
        }
    }

    /**
     * Context-derived ACL (RF-1.5/1.9). ADMIN_DESIGNER always has access (implicit in hasPermission).
     * <ul>
     *   <li>Client upload: the client owns it (read/write/delete); participating departments may read.</li>
     *   <li>Functionary upload: the uploader's department owns it (read/write/delete, department-scoped
     *       co-edit); the client and other participating departments may read.</li>
     * </ul>
     */
    private DocumentPermissions deriveAcl(boolean clientUpload, String userId, String departmentId,
                                          String clientId, Set<String> policyDepartments) {
        Set<String> read = new LinkedHashSet<>();
        Set<String> write = new LinkedHashSet<>();
        read.add(ROLE_ADMIN);
        write.add(ROLE_ADMIN);
        if (clientId != null && !clientId.isBlank()) read.add(clientId);

        if (clientUpload) {
            if (clientId != null && !clientId.isBlank()) write.add(clientId);
            else if (userId != null) write.add(userId);
            read.addAll(policyDepartments);
        } else if (departmentId != null && !departmentId.isBlank()) {
            read.add(departmentId);
            write.add(departmentId);
            read.addAll(policyDepartments);
        } else if (userId != null) {
            read.add(userId);
            write.add(userId);
        }
        return new DocumentPermissions(
                new ArrayList<>(read), new ArrayList<>(write), new ArrayList<>(write));
    }

    private boolean canRead(ProcessDocument doc, String userId, String userRole, String departmentId) {
        return hasPermission(doc.getPermissions() != null
                ? doc.getPermissions().getCanRead() : null, userId, userRole, departmentId);
    }

    private boolean canWrite(ProcessDocument doc, String userId, String userRole, String departmentId) {
        return hasPermission(doc.getPermissions() != null
                ? doc.getPermissions().getCanWrite() : null, userId, userRole, departmentId);
    }

    private boolean canDelete(ProcessDocument doc, String userId, String userRole, String departmentId) {
        return hasPermission(doc.getPermissions() != null
                ? doc.getPermissions().getCanDelete() : null, userId, userRole, departmentId);
    }

    /**
     * Returns true if the caller's userId, role OR departmentId appears in the permission list.
     * ADMIN_DESIGNER always has access (RF-1.5/1.9).
     */
    private boolean hasPermission(List<String> allowed, String userId, String userRole, String departmentId) {
        if (ROLE_ADMIN.equals(userRole)) return true;
        if (allowed == null || allowed.isEmpty()) return false;
        if (allowed.contains(userId) || allowed.contains(userRole)) return true;
        return departmentId != null && !departmentId.isBlank() && allowed.contains(departmentId);
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

    private AuditLogResponse toAuditResponse(DocumentAuditLog log, String userName) {
        return new AuditLogResponse(
                log.getId(),
                log.getDocumentId(),
                log.getProcessInstanceId(),
                log.getUserId(),
                log.getUserRole(),
                log.getAction(),
                log.getTimestamp(),
                log.getIpAddress(),
                log.getDetail(),
                userName
        );
    }
}
