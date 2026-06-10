package com.ibpms.service.impl;

import com.ibpms.domain.DocumentAuditLog;
import com.ibpms.domain.DocumentPermissions;
import com.ibpms.domain.DocumentVersion;
import com.ibpms.domain.ProcessDocument;
import com.ibpms.domain.enums.DocumentAction;
import com.ibpms.dto.request.OnlyOfficeCallbackRequest;
import com.ibpms.dto.response.OnlyOfficeConfigResponse;
import com.ibpms.exception.DocumentNotFoundException;
import com.ibpms.repository.DocumentAuditLogRepository;
import com.ibpms.repository.ProcessDocumentRepository;
import com.ibpms.service.api.OnlyOfficeService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OnlyOfficeServiceImpl implements OnlyOfficeService {

    // OnlyOffice callback status codes.
    private static final int STATUS_READY_TO_SAVE = 2;
    private static final int STATUS_FORCE_SAVE = 6;

    private final ProcessDocumentRepository documentRepository;
    private final DocumentAuditLogRepository auditLogRepository;
    private final S3Service s3Service;
    private final String documentServerUrl;
    private final String callbackBaseUrl;
    private final SecretKey jwtKey;          // null if no secret configured (JWT disabled)

    public OnlyOfficeServiceImpl(ProcessDocumentRepository documentRepository,
                                 DocumentAuditLogRepository auditLogRepository,
                                 S3Service s3Service,
                                 @Value("${onlyoffice.document-server-url}") String documentServerUrl,
                                 @Value("${onlyoffice.callback-base-url}") String callbackBaseUrl,
                                 @Value("${onlyoffice.jwt-secret:}") String jwtSecret) {
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.s3Service = s3Service;
        this.documentServerUrl = stripTrailingSlash(documentServerUrl);
        this.callbackBaseUrl = stripTrailingSlash(callbackBaseUrl);
        this.jwtKey = (jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 32)
                ? Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)) : null;
    }

    @Override
    public OnlyOfficeConfigResponse buildEditorConfig(String documentId, String userId, String userRole,
                                                      String departmentId, String ipAddress) {
        ProcessDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String fileName = doc.getFileName() != null ? doc.getFileName() : "document";
        String ext = extension(fileName);
        String documentType = documentType(ext);
        // Edit vs view-only is resolved from the document ACL (RF-1.9): only principals in canWrite
        // (the owning department, the owner, ADMIN) edit; everyone else with read opens view-only.
        boolean canEdit = canWriteDoc(doc, userId, userRole, departmentId);

        // RF-1.6: record who opened the document and when.
        recordAudit(doc, userId, userRole, DocumentAction.VIEW, ipAddress,
                "Abrió el documento en el editor (" + (canEdit ? "edición" : "solo lectura") + ")");

        // OnlyOffice caches by "key"; it MUST change whenever the stored content changes,
        // so we derive it from the current S3 key.
        String key = sanitizeKey(documentId + "_" + Integer.toHexString(
                doc.getS3Key() != null ? doc.getS3Key().hashCode() : 0));

        // The Document Server downloads the file from the backend (not a presigned S3 URL —
        // its HTTP client mangles the SigV4 signature). Authenticated by a short-lived token.
        String contentToken = (jwtKey != null)
                ? Jwts.builder().subject(documentId)
                        .expiration(Date.from(Instant.now().plus(Duration.ofHours(2))))
                        .signWith(jwtKey).compact()
                : "none";
        String fileUrl = callbackBaseUrl + "/api/v1/documents/" + documentId
                + "/onlyoffice/content?dt=" + contentToken;
        String callbackUrl = callbackBaseUrl + "/api/v1/documents/onlyoffice/callback?documentId=" + documentId;

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", ext);
        document.put("key", key);
        document.put("title", fileName);
        document.put("url", fileUrl);
        Map<String, Object> docPerms = new LinkedHashMap<>();
        docPerms.put("edit", canEdit);
        docPerms.put("download", true);
        document.put("permissions", docPerms);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("name", userId);

        // customization: forceSave shows an explicit "Save" button; the DS immediately calls
        // the callback with status=6 so the backend persists to S3 without closing the tab.
        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forceSave", true);
        customization.put("autosave", true);      // also auto-saves periodically in the DS cache
        customization.put("spellcheck", false);   // reduces noise in the UI

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", canEdit ? "edit" : "view");
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", user);
        editorConfig.put("lang", "es");
        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", documentType);
        config.put("document", document);
        config.put("editorConfig", editorConfig);
        config.put("type", "desktop");

        // Sign the whole config (OnlyOffice verifies it when JWT is enabled on the DS).
        if (jwtKey != null) {
            String token = Jwts.builder().claims(config).signWith(jwtKey).compact();
            config.put("token", token);
        }

        return new OnlyOfficeConfigResponse(documentServerUrl, documentType, config);
    }

    @Override
    public void handleCallback(String documentId, OnlyOfficeCallbackRequest callback) {
        if (callback == null || callback.status() == null) return;

        // Verify the DS-issued token when JWT is enabled (defense-in-depth; endpoint is public).
        if (jwtKey != null && callback.token() != null) {
            try {
                Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(callback.token());
            } catch (Exception e) {
                throw new SecurityException("Token de callback OnlyOffice inválido");
            }
        }

        int status = callback.status();
        if (status != STATUS_READY_TO_SAVE && status != STATUS_FORCE_SAVE) {
            return; // editing / closed-without-changes / error → nothing to persist
        }
        if (callback.url() == null || callback.url().isBlank()) return;

        ProcessDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        byte[] edited = download(callback.url());

        // Append the current key as a historical version (RF-07) and store the edited content
        // under a fresh key so OnlyOffice's content "key" changes on next open.
        String oldKey = doc.getS3Key();
        String newKey = deriveNewVersionKey(oldKey);
        s3Service.putObject(newKey, edited, doc.getMimeType());

        List<DocumentVersion> versions = doc.getVersions() != null ? doc.getVersions() : new ArrayList<>();
        if (oldKey != null) {
            versions.add(new DocumentVersion(UUID.randomUUID().toString(), oldKey,
                    doc.getUploadedBy(), doc.getUploadedAt(), (long) edited.length));
        }
        doc.setVersions(versions);
        doc.setS3Key(newKey);
        documentRepository.save(doc);

        // RF-1.6: record who edited the document in this collaborative session.
        List<String> editors = callback.users();
        if (editors != null && !editors.isEmpty()) {
            for (String editorId : editors) {
                recordAudit(doc, editorId, null, DocumentAction.REPLACE, null,
                        "Editó el documento vía OnlyOffice (sesión colaborativa)");
            }
        } else {
            recordAudit(doc, null, null, DocumentAction.REPLACE, null,
                    "Documento guardado vía OnlyOffice");
        }
    }

    @Override
    public DocumentContent getDocumentContent(String documentId, String token) {
        // Validate the short-lived token issued in buildEditorConfig (defense-in-depth:
        // the endpoint is public so the Document Server can reach it without a user JWT).
        if (jwtKey != null) {
            try {
                String subject = Jwts.parser().verifyWith(jwtKey).build()
                        .parseSignedClaims(token).getPayload().getSubject();
                if (!documentId.equals(subject)) {
                    throw new SecurityException("Token de contenido no corresponde al documento");
                }
            } catch (Exception e) {
                throw new SecurityException("Token de contenido OnlyOffice inválido");
            }
        }

        ProcessDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        byte[] bytes = s3Service.getObject(doc.getS3Key());
        return new DocumentContent(bytes, doc.getMimeType(), doc.getFileName());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static byte[] download(String url) {
        try (var in = URI.create(url).toURL().openStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descargar el documento editado de OnlyOffice", e);
        }
    }

    /** Insert a "_v{uuid}" segment before the filename so the new version gets a unique key. */
    private static String deriveNewVersionKey(String oldKey) {
        String marker = "v" + UUID.randomUUID().toString().substring(0, 8);
        if (oldKey == null || oldKey.isBlank()) return marker;
        int slash = oldKey.lastIndexOf('/');
        if (slash < 0) return marker + "_" + oldKey;
        return oldKey.substring(0, slash + 1) + marker + "_" + oldKey.substring(slash + 1);
    }

    /**
     * Edit permission resolved from the document ACL (RF-1.9): a principal that appears in
     * {@code canWrite} (by userId, role or departmentId) edits; ADMIN_DESIGNER always edits.
     * Everyone else opens the document in view-only mode.
     */
    private boolean canWriteDoc(ProcessDocument doc, String userId, String userRole, String departmentId) {
        if ("ADMIN_DESIGNER".equals(userRole)) return true;
        DocumentPermissions p = doc.getPermissions();
        if (p == null || p.getCanWrite() == null) return false;
        List<String> w = p.getCanWrite();
        if (w.contains(userId) || w.contains(userRole)) return true;
        return departmentId != null && !departmentId.isBlank() && w.contains(departmentId);
    }

    private void recordAudit(ProcessDocument doc, String userId, String userRole,
                             DocumentAction action, String ipAddress, String detail) {
        DocumentAuditLog log = new DocumentAuditLog();
        log.setDocumentId(doc.getId());
        log.setProcessInstanceId(doc.getProcessInstanceId());
        log.setUserId(userId);
        log.setUserRole(userRole);
        log.setAction(action);
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    private static String documentType(String ext) {
        return switch (ext) {
            case "xls", "xlsx", "csv", "ods" -> "cell";
            case "ppt", "pptx", "odp" -> "slide";
            default -> "word";
        };
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase() : "docx";
    }

    private static String sanitizeKey(String key) {
        String k = key.replaceAll("[^0-9a-zA-Z_-]", "");
        return k.length() > 120 ? k.substring(0, 120) : k;
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
