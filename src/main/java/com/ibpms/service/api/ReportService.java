package com.ibpms.service.api;

import com.ibpms.dto.response.ReportSpec;
import com.ibpms.dto.response.ReportTable;

/**
 * Dynamic report engine (RF-4): interprets a natural-language request via ibpms_ia (Gemini)
 * and executes it against MongoDB over a bounded query universe.
 */
public interface ReportService {

    /** NL instruction → bounded {@link ReportSpec} (delegated to ibpms_ia / Gemini). */
    ReportSpec interpret(String instruction);

    /** Execute a spec against MongoDB and build the tabular result. */
    ReportTable buildTable(ReportSpec spec);

    /**
     * Render a table to a file.
     *
     * @param format explicit format — EXCEL | WORD | PDF. Overrides whatever ibpms_ia put in the spec.
     */
    byte[] render(ReportTable table, String format);
}
