package com.ibpms.controller;

import com.ibpms.dto.request.OnlyOfficeCallbackRequest;
import com.ibpms.dto.response.OnlyOfficeConfigResponse;
import com.ibpms.service.api.OnlyOfficeService;
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
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String userRole = authentication.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(onlyOfficeService.buildEditorConfig(id, userId, userRole));
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
