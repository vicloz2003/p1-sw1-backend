package com.ibpms.controller;

import com.ibpms.dto.request.OnlyOfficeCallbackRequest;
import com.ibpms.dto.response.OnlyOfficeConfigResponse;
import com.ibpms.service.api.OnlyOfficeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Collaborative Office editing endpoints (RF-1.10) backed by the OnlyOffice Document Server.
 *
 * <p>{@code GET .../onlyoffice/config} returns the signed editor config for the Angular client.
 * {@code POST .../onlyoffice/callback} is called by the Document Server itself (public at the
 * HTTP layer — see SecurityConfig — and authenticated via the OnlyOffice JWT).
 */
@RestController
@RequestMapping("/api/v1/documents")
public class OnlyOfficeController {

    private final OnlyOfficeService onlyOfficeService;

    public OnlyOfficeController(OnlyOfficeService onlyOfficeService) {
        this.onlyOfficeService = onlyOfficeService;
    }

    @GetMapping("/{id}/onlyoffice/config")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<OnlyOfficeConfigResponse> getConfig(
            @PathVariable String id,
            Authentication authentication,
            @RequestAttribute(value = "departmentId", required = false) String departmentId,
            HttpServletRequest httpRequest) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        String ip = extractIp(httpRequest);
        return ResponseEntity.ok(onlyOfficeService.buildEditorConfig(id, userId, userRole, departmentId, ip));
    }

    private static String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    /**
     * Streams the raw document bytes for the Document Server to download (RF-1.10).
     * Public at the HTTP layer (the DS has no user JWT); authenticated by the signed
     * {@code dt} token issued in the editor config.
     */
    @GetMapping("/{id}/onlyoffice/content")
    public ResponseEntity<byte[]> getContent(
            @PathVariable String id,
            @RequestParam("dt") String token) {
        OnlyOfficeService.DocumentContent c = onlyOfficeService.getDocumentContent(id, token);
        MediaType type = c.mimeType() != null
                ? MediaType.parseMediaType(c.mimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).body(c.content());
    }

    /**
     * Document Server save callback. Must always answer {@code {"error": 0}} on success,
     * otherwise OnlyOffice retries indefinitely.
     */
    @PostMapping("/onlyoffice/callback")
    public ResponseEntity<Map<String, Integer>> callback(
            @RequestParam String documentId,
            @RequestBody OnlyOfficeCallbackRequest callback) {
        onlyOfficeService.handleCallback(documentId, callback);
        return ResponseEntity.ok(Map.of("error", 0));
    }
}
