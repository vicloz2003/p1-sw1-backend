package com.ibpms.controller;

import com.ibpms.dto.request.ReportRequest;
import com.ibpms.dto.response.ReportScreenResponse;
import com.ibpms.dto.response.ReportSpec;
import com.ibpms.dto.response.ReportTable;
import com.ibpms.service.api.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic reports for the policy manager (RF-4). The manager describes — by voice (already
 * transcribed to text) or by typing — what data, conditions and format they want; the system
 * interprets it via ibpms_ia (Gemini), runs the bounded query and returns the result either
 * on screen (JSON) or as a downloadable Excel/Word/PDF file.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@Valid @RequestBody ReportRequest request) {
        ReportSpec spec = reportService.interpret(request.instruction());
        ReportTable table = reportService.buildTable(spec);

        // Explicit format in the request always wins over whatever Gemini inferred.
        String format = (request.format() != null && !request.format().isBlank())
                ? request.format().toUpperCase()
                : (spec.format() == null ? "SCREEN" : spec.format());
        if ("SCREEN".equals(format)) {
            return ResponseEntity.ok(new ReportScreenResponse(spec, table));
        }

        byte[] content = reportService.render(table, format);
        String filename = sanitize(spec.title()) + extension(format);
        MediaType type = switch (format) {
            case "WORD" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "PDF" -> MediaType.APPLICATION_PDF;
            default -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private static String extension(String format) {
        return switch (format) {
            case "WORD" -> ".docx";
            case "PDF" -> ".pdf";
            default -> ".xlsx";
        };
    }

    private static String sanitize(String title) {
        String base = (title == null || title.isBlank()) ? "reporte" : title.trim();
        return base.replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
    }
}
