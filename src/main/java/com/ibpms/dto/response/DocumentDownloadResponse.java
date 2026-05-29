package com.ibpms.dto.response;

import java.time.LocalDateTime;

/**
 * Returned by {@code GET /api/v1/documents/{id}/download} (RF-06).
 *
 * <p>The client opens {@code presignedUrl} directly in a browser / HTTP client
 * to download the file from S3. The URL expires at {@code expiresAt}.
 */
public record DocumentDownloadResponse(
        String documentId,
        String fileName,
        String presignedUrl,
        LocalDateTime expiresAt
) {}
