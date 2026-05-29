package com.ibpms.dto.response;

import com.ibpms.domain.DocumentPermissions;
import com.ibpms.domain.DocumentVersion;
import com.ibpms.domain.enums.DocumentStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Returned for any document metadata query (RF-04).
 * Does NOT include presigned URLs — those are returned by the dedicated
 * download endpoint (RF-06).
 */
public record DocumentResponse(
        String id,
        String processInstanceId,
        String businessPolicyId,
        String documentRequirementId,
        String fileName,
        String mimeType,
        String uploadedBy,
        String uploadedByRole,
        DocumentStatus status,
        DocumentPermissions permissions,
        List<DocumentVersion> versions,
        LocalDateTime uploadedAt,
        LocalDateTime confirmedAt,
        String taskId
) {}
