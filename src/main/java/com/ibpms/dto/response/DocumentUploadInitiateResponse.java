package com.ibpms.dto.response;

/**
 * Returned by {@code POST /api/v1/documents/initiate} (RF-02 / RF-03).
 *
 * <p>The client uses {@code presignedUrl} to PUT the file directly to S3,
 * then calls {@code POST /api/v1/documents/{id}/confirm} to mark it CONFIRMED.
 */
public record DocumentUploadInitiateResponse(
        String documentId,
        String s3Key,
        String presignedUrl
) {}
