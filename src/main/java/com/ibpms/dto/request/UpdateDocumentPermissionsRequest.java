package com.ibpms.dto.request;

import java.util.List;

/**
 * RF-1.5 / RF-1.9: reassign the ACL of a document.
 *
 * <p>Each list may contain userId strings, departmentId strings or role
 * identifiers (e.g. {@code "ADMIN_DESIGNER"}). A {@code null} list leaves that
 * permission unchanged; an empty list clears it. Only ADMIN_DESIGNER may call this.
 */
public record UpdateDocumentPermissionsRequest(
        List<String> canRead,
        List<String> canWrite,
        List<String> canDelete
) {}
