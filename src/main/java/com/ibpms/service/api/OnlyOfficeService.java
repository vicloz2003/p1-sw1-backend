package com.ibpms.service.api;

import com.ibpms.dto.request.OnlyOfficeCallbackRequest;
import com.ibpms.dto.response.OnlyOfficeConfigResponse;

/**
 * Collaborative Office editing via the OnlyOffice Document Server (RF-1.10).
 * Spring Boot builds the (JWT-signed) editor config and persists edits back to S3 through
 * the Document Server callback.
 */
public interface OnlyOfficeService {

    /**
     * Build the signed editor config for a document, with edit/view mode resolved from the
     * document ACL (RF-1.9). Records a VIEW audit entry (RF-1.6).
     */
    OnlyOfficeConfigResponse buildEditorConfig(String documentId, String userId, String userRole,
                                               String departmentId, String ipAddress);

    /** Handle a Document Server callback: on save, download the file and store a new S3 version. */
    void handleCallback(String documentId, OnlyOfficeCallbackRequest callback);

    /**
     * Returns the document bytes for the Document Server to download (RF-1.10).
     * The {@code token} is the short-lived signed token embedded in the content URL.
     */
    DocumentContent getDocumentContent(String documentId, String token);

    /** Raw document content served to the Document Server. */
    record DocumentContent(byte[] content, String mimeType, String fileName) {}
}
