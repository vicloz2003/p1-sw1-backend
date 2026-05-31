package com.ibpms.dto.request;

import java.util.List;

/**
 * Payload the OnlyOffice Document Server POSTs to the callback when an editing session
 * changes state (RF-1.10). Unknown fields are ignored by Jackson.
 *
 * <p>status: 1=editing, 2=ready to save (closed), 3=save error, 4=closed no changes,
 * 6=force-save while still editing, 7=force-save error.
 */
public record OnlyOfficeCallbackRequest(
        Integer status,
        String url,
        String key,
        List<String> users,
        String token
) {}
