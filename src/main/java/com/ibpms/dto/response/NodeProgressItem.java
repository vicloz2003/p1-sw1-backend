package com.ibpms.dto.response;

import java.time.LocalDateTime;

/**
 * Represents a single node in the visual timeline of a ProcessInstance.
 * Used in {@link ProcessStatusResponse} to show the client the progress of their tramite.
 */
public record NodeProgressItem(
        String nodeId,
        String nodeLabel,
        String departmentName,
        String progressStatus,   // "COMPLETED" | "CURRENT" | "PENDING"
        LocalDateTime completedAt
) {}
