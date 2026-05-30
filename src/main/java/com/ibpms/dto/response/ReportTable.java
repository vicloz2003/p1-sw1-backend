package com.ibpms.dto.response;

import java.util.List;

/** Tabular result of a dynamic report: a title, column headers and string-formatted rows. */
public record ReportTable(
        String title,
        List<String> headers,
        List<List<String>> rows
) {}
