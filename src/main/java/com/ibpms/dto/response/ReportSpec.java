package com.ibpms.dto.response;

import java.util.List;

/**
 * Structured, bounded report specification produced by ibpms_ia (Gemini NLP) from the
 * manager's natural-language request (RF-4). The backend executes it against MongoDB.
 */
public record ReportSpec(
        String title,
        String dataset,
        List<String> metrics,
        String groupBy,
        String filterStatus,
        String filterPolicyName,
        String dateFrom,
        String dateTo,
        String sortBy,
        String sortDir,
        String format,
        String interpretedBy
) {}
