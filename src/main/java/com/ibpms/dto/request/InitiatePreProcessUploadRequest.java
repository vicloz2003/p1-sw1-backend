package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/v1/documents/pre-process} (RF-01).
 *
 * <p>Used when a CLIENT needs to upload mandatory PROCESS_START documents
 * before the process instance exists. The resulting {@code ProcessDocument}
 * is stored with {@code processInstanceId = null} and linked once
 * {@code POST /api/v1/processes} succeeds.
 */
public record InitiatePreProcessUploadRequest(

        @NotBlank(message = "policyId is required")
        String policyId,

        @NotBlank(message = "documentRequirementId is required")
        String documentRequirementId,

        @NotBlank(message = "fileName is required")
        String fileName,

        @NotBlank(message = "mimeType is required")
        String mimeType,

        /**
         * Owner client of the document (RF-1.4). Optional: when null, the service
         * uses the authenticated uploader's id (the CLIENT uploading their own docs
         * via the agent). An EMPLOYEE uploading on behalf of a client sets it explicitly.
         */
        String clientId
) {}
