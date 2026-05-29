package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/v1/documents/initiate} (RF-02 / RF-03).
 *
 * <p>Triggers the creation of a {@code ProcessDocument} record in PENDING_UPLOAD
 * state and returns a presigned S3 PUT URL.
 */
public record InitiateDocumentUploadRequest(

        @NotBlank(message = "processInstanceId is required")
        String processInstanceId,

        /**
         * References a {@link com.ibpms.domain.DocumentRequirement#getId()}.
         * Null if this is an ad-hoc document attached during an ACTION node.
         */
        String documentRequirementId,

        @NotBlank(message = "fileName is required")
        String fileName,

        @NotBlank(message = "mimeType is required")
        String mimeType,

        /**
         * taskId of the ACTION node task this document belongs to.
         * Null when the document is uploaded at process start.
         */
        String taskId
) {}
