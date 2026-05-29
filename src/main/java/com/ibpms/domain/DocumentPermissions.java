package com.ibpms.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Access-control list embedded in {@link ProcessDocument}.
 *
 * <p>Each list may contain userId strings, departmentId strings or
 * role identifiers (e.g. {@code "ADMIN_DESIGNER"}). The service layer
 * resolves them against the authenticated principal at request time.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPermissions {
    private List<String> canRead;
    private List<String> canWrite;
    private List<String> canDelete;
}
