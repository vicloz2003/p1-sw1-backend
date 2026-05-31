package com.ibpms.service.impl;

import com.ibpms.domain.DocumentVersion;
import com.ibpms.domain.ProcessDocument;
import com.ibpms.dto.request.OnlyOfficeCallbackRequest;
import com.ibpms.dto.response.OnlyOfficeConfigResponse;
import com.ibpms.exception.DocumentNotFoundException;
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
import java.time.LocalDateTime;
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
    private final S3Service s3Service;
    private final String documentServerUrl;
    private final String callbackBaseUrl;
    private final SecretKey jwtKey;          // null if no secret configured (JWT disabled)

    public OnlyOfficeServiceImpl(ProcessDocumentRepository documentRepository,
                                 S3Service s3Service,
                                 @Value("${onlyoffice.document-server-url}") String documentServerUrl,
                                 @Value("${onlyoffice.callback-base-url}") String callbackBaseUrl,
                                 @Value("${onlyoffice.jwt-secret:}") String jwtSecret) {
        this.documentRepository = documentRepository;
        this.s3Service = s3Service;
        this.documentServerUrl = stripTrailingSlash(documentServerUrl);
        this.callbackBaseUrl = stripTrailingSlash(callbackBaseUrl);
        this.jwtKey = (jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 32)
                ? Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)) : null;
    }

    @Override
    public OnlyOfficeConfigResponse buildEditorConfig(String documentId, String userId, String userRole) {
        ProcessDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String fileName = doc.getFileName() != null ? doc.getFileName() : "document";
        String ext = extension(fileName);
        String documentType = documentType(ext);
        boolean canEdit = canEdit(userRole, documentType);

        // OnlyOffice caches by "key"; it MUST change whenever the stored content changes,
        // so we derive it from the current S3 key.
        String key = sanitizeKey(documentId + "_" + Integer.toHexString(
                doc.getS3Key() != null ? doc.getS3Key().hashCode() : 0));

        String fileUrl = s3Service.generatePresignedGetUrl(doc.getS3Key(), Duration.ofHours(1));
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

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", canEdit ? "edit" : "view");
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", user);
        editorConfig.put("lang", "es");

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

    private static boolean canEdit(String role, String documentType) {
        // Office documents are edited by staff; clients open them read-only.
        // Fine-grained per-department ACL (RF-1.9) refines this at the document level.
        return "ADMIN_DESIGNER".equals(role) || "EMPLOYEE".equals(role);
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
