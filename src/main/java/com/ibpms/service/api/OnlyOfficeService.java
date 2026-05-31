package com.ibpms.service.api;

import com.ibpms.dto.request.OnlyOfficeCallbackRequest;
import com.ibpms.dto.response.OnlyOfficeConfigResponse;

/**
 * Collaborative Office editing via the OnlyOffice Document Server (RF-1.10).
 * Spring Boot builds the (JWT-signed) editor config and persists edits back to S3 through
 * the Document Server callback.
 */
public interface OnlyOfficeService {

    /** Build the signed editor config for a document, with edit/view mode by role/permission. */
    OnlyOfficeConfigResponse buildEditorConfig(String documentId, String userId, String userRole);

    /** Handle a Document Server callback: on save, download the file and store a new S3 version. */
    void handleCallback(String documentId, OnlyOfficeCallbackRequest callback);
}
