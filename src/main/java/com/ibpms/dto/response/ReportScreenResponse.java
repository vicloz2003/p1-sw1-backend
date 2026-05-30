package com.ibpms.dto.response;

/** On-screen report payload (format=SCREEN): the interpreted spec plus the computed table. */
public record ReportScreenResponse(
        ReportSpec spec,
        ReportTable table
) {}
