package com.ibpms.dto.response;

import com.ibpms.domain.enums.DocumentAction;

import java.time.LocalDateTime;

/**
 * Returned by {@code GET /api/v1/documents/{id}/audit} (RF-08).
 */
public record AuditLogResponse(
        String id,
        String documentId,
        String processInstanceId,
        String userId,
        String userRole,
        DocumentAction action,
        LocalDateTime timestamp,
        String ipAddress,
        String detail
) {}
