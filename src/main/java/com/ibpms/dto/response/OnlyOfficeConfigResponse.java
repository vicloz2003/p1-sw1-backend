package com.ibpms.dto.response;

import java.util.Map;

/**
 * Everything the Angular OnlyOffice editor needs to open a document for collaborative
 * editing (RF-1.10): the Document Server URL to load the API from, the document type, and
 * the editor {@code config} object (already including the signed {@code token}).
 */
public record OnlyOfficeConfigResponse(
        String documentServerUrl,
        String documentType,
        Map<String, Object> config
) {}
