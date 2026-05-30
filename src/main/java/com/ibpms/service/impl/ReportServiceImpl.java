package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.Department;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.User;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.ReportSpec;
import com.ibpms.dto.response.ReportTable;
import com.ibpms.exception.AgentUnavailableException;
import com.ibpms.repository.*;
import com.ibpms.service.api.ReportService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    private final ProcessInstanceRepository instanceRepository;
    private final ActivityTaskRepository taskRepository;
    private final BusinessPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RestClient iaRestClient;

    public ReportServiceImpl(ProcessInstanceRepository instanceRepository,
                             ActivityTaskRepository taskRepository,
                             BusinessPolicyRepository policyRepository,
                             UserRepository userRepository,
                             DepartmentRepository departmentRepository,
                             RestClient iaRestClient) {
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.iaRestClient = iaRestClient;
    }

    // ── NL → spec (ibpms_ia / Gemini) ─────────────────────────────────────────
    @Override
    public ReportSpec interpret(String instruction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction", instruction);
        try {
            return iaRestClient.post()
                    .uri("/api/ia/interpret-report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(ReportSpec.class);
        } catch (Exception e) {
            throw new AgentUnavailableException(
                    "No se pudo interpretar la solicitud de reporte (servicio de IA no disponible).", e);
        }
    }

    // ── spec → tabular data ───────────────────────────────────────────────────
    @Override
    public ReportTable buildTable(ReportSpec spec) {
        return switch (spec.dataset()) {
            case "tasks" -> buildTasks(spec);
            case "policies" -> buildPolicies(spec);
            default -> buildProcesses(spec);
        };
    }

    private ReportTable buildProcesses(ReportSpec spec) {
        Map<String, String> policyNameById = policyRepository.findAll().stream()
                .collect(Collectors.toMap(BusinessPolicy::getId, p -> safe(p.getName())));
        String policyIdFilter = resolvePolicyId(spec.filterPolicyName());
        LocalDateTime from = parseDate(spec.dateFrom(), true);
        LocalDateTime to = parseDate(spec.dateTo(), false);
        InstanceStatus statusFilter = parseInstanceStatus(spec.filterStatus());

        List<ProcessInstance> data = instanceRepository.findAll().stream()
                .filter(i -> policyIdFilter == null || policyIdFilter.equals(i.getBusinessPolicyId()))
                .filter(i -> statusFilter == null || i.getStatus() == statusFilter)
                .filter(i -> inRange(i.getStartedAt(), from, to))
                .toList();

        // group key
        Map<String, List<ProcessInstance>> groups = data.stream().collect(Collectors.groupingBy(
                i -> switch (nullToEmpty(spec.groupBy())) {
                    case "status" -> i.getStatus() != null ? i.getStatus().name() : "—";
                    case "month" -> i.getStartedAt() != null ? YearMonth.from(i.getStartedAt()).toString() : "—";
                    case "client" -> safe(i.getClientId());
                    case "policy" -> policyNameById.getOrDefault(i.getBusinessPolicyId(), safe(i.getBusinessPolicyId()));
                    default -> "Total";
                }, LinkedHashMap::new, Collectors.toList()));

        List<Row> rows = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<ProcessInstance> g = e.getValue();
            long total = g.size();
            long completed = g.stream().filter(i -> i.getStatus() == InstanceStatus.COMPLETED).count();
            long cancelled = g.stream().filter(i -> i.getStatus() == InstanceStatus.CANCELLED).count();
            double avgDuration = g.stream()
                    .filter(i -> i.getStatus() == InstanceStatus.COMPLETED
                            && i.getStartedAt() != null && i.getCompletedAt() != null)
                    .mapToDouble(i -> Duration.between(i.getStartedAt(), i.getCompletedAt()).toMinutes() / 60.0)
                    .average().orElse(0.0);
            Map<String, Double> m = new HashMap<>();
            m.put("count", (double) total);
            m.put("avgDurationHours", avgDuration);
            m.put("completionRate", total > 0 ? completed * 100.0 / total : 0.0);
            m.put("cancelledRate", total > 0 ? cancelled * 100.0 / total : 0.0);
            rows.add(new Row(e.getKey(), m));
        }
        return assemble(spec, groupLabel(spec.groupBy()), rows);
    }

    private ReportTable buildTasks(ReportSpec spec) {
        Map<String, String> usernameById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> safe(u.getUsername())));
        Map<String, String> deptNameById = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::id, d -> safe(d.name())));
        Map<String, String> nodeLabelById = policyRepository.findAll().stream()
                .filter(p -> p.getNodes() != null).flatMap(p -> p.getNodes().stream())
                .collect(Collectors.toMap(ActivityNode::getId, n -> safe(n.getLabel()), (a, b) -> a));

        LocalDateTime from = parseDate(spec.dateFrom(), true);
        LocalDateTime to = parseDate(spec.dateTo(), false);
        TaskStatus statusFilter = parseTaskStatus(spec.filterStatus());

        List<ActivityTask> data = taskRepository.findAll().stream()
                .filter(t -> statusFilter == null || t.getStatus() == statusFilter)
                .filter(t -> inRange(t.getAssignedAt(), from, to))
                .toList();

        Map<String, List<ActivityTask>> groups = data.stream().collect(Collectors.groupingBy(
                t -> switch (nullToEmpty(spec.groupBy())) {
                    case "user" -> usernameById.getOrDefault(t.getAssignedUserId(), "Sin asignar");
                    case "node" -> nodeLabelById.getOrDefault(t.getNodeId(), safe(t.getNodeId()));
                    case "status" -> t.getStatus() != null ? t.getStatus().name() : "—";
                    case "department" -> deptNameById.getOrDefault(t.getAssignedDepartmentId(), safe(t.getAssignedDepartmentId()));
                    default -> "Total";
                }, LinkedHashMap::new, Collectors.toList()));

        List<Row> rows = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<ActivityTask> g = e.getValue();
            double avgWork = g.stream()
                    .filter(t -> t.getStartedAt() != null && t.getCompletedAt() != null)
                    .mapToDouble(t -> Duration.between(t.getStartedAt(), t.getCompletedAt()).toMinutes() / 60.0)
                    .average().orElse(0.0);
            Map<String, Double> m = new HashMap<>();
            m.put("count", (double) g.size());
            m.put("avgWorkHours", avgWork);
            rows.add(new Row(e.getKey(), m));
        }
        return assemble(spec, groupLabel(spec.groupBy()), rows);
    }

    private ReportTable buildPolicies(ReportSpec spec) {
        List<BusinessPolicy> policies = policyRepository.findAll();
        List<Row> rows = new ArrayList<>();
        if ("status".equals(spec.groupBy())) {
            Map<String, List<BusinessPolicy>> byStatus = policies.stream()
                    .collect(Collectors.groupingBy(p -> p.getStatus() != null ? p.getStatus().name() : "—",
                            LinkedHashMap::new, Collectors.toList()));
            for (var e : byStatus.entrySet()) {
                rows.add(new Row(e.getKey(), policyMetrics(e.getValue())));
            }
            return assemble(spec, "Estado", rows);
        }
        for (BusinessPolicy p : policies) {
            rows.add(new Row(safe(p.getName()), policyMetrics(List.of(p))));
        }
        return assemble(spec, "Política", rows);
    }

    private Map<String, Double> policyMetrics(List<BusinessPolicy> policies) {
        long instances = 0, cancelled = 0;
        double durSum = 0; long durCount = 0;
        for (BusinessPolicy p : policies) {
            List<ProcessInstance> inst = instanceRepository.findByBusinessPolicyId(p.getId());
            instances += inst.size();
            cancelled += inst.stream().filter(i -> i.getStatus() == InstanceStatus.CANCELLED).count();
            for (ProcessInstance i : inst) {
                if (i.getStatus() == InstanceStatus.COMPLETED && i.getStartedAt() != null && i.getCompletedAt() != null) {
                    durSum += Duration.between(i.getStartedAt(), i.getCompletedAt()).toMinutes() / 60.0;
                    durCount++;
                }
            }
        }
        Map<String, Double> m = new HashMap<>();
        m.put("instanceCount", (double) instances);
        m.put("abandonmentRate", instances > 0 ? cancelled * 100.0 / instances : 0.0);
        m.put("avgDurationHours", durCount > 0 ? durSum / durCount : 0.0);
        return m;
    }

    /** Sort, format and assemble the final string table from grouped metric rows. */
    private ReportTable assemble(ReportSpec spec, String groupHeader, List<Row> rows) {
        String sortKey = spec.sortBy() != null && !spec.metrics().isEmpty()
                && spec.metrics().contains(spec.sortBy()) ? spec.sortBy() : spec.metrics().get(0);
        Comparator<Row> cmp = Comparator.comparingDouble(r -> r.values.getOrDefault(sortKey, 0.0));
        if (!"asc".equalsIgnoreCase(spec.sortDir())) cmp = cmp.reversed();
        rows.sort(cmp);

        List<String> headers = new ArrayList<>();
        headers.add(groupHeader);
        for (String metric : spec.metrics()) headers.add(metricLabel(metric));

        List<List<String>> outRows = new ArrayList<>();
        for (Row r : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(r.label);
            for (String metric : spec.metrics()) cells.add(formatMetric(metric, r.values.getOrDefault(metric, 0.0)));
            outRows.add(cells);
        }
        return new ReportTable(spec.title(), headers, outRows);
    }

    // ── rendering ──────────────────────────────────────────────────────────────
    @Override
    public byte[] render(ReportSpec spec, ReportTable table) {
        return switch (nullToEmpty(spec.format())) {
            case "WORD" -> renderWord(table);
            case "PDF" -> renderPdf(table);
            default -> renderExcel(table);
        };
    }

    private byte[] renderExcel(ReportTable table) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Reporte");
            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hf = wb.createFont();
            hf.setBold(true);
            headerStyle.setFont(hf);

            var header = sheet.createRow(0);
            for (int c = 0; c < table.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(table.headers().get(c));
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (List<String> row : table.rows()) {
                var excelRow = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) excelRow.createCell(c).setCellValue(row.get(c));
            }
            for (int c = 0; c < table.headers().size(); c++) sheet.autoSizeColumn(c);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando Excel", e);
        }
    }

    private byte[] renderWord(ReportTable table) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun tr = title.createRun();
            tr.setBold(true);
            tr.setFontSize(15);
            tr.setText(safe(table.title()));

            XWPFTable t = doc.createTable();
            XWPFTableRow head = t.getRow(0);
            for (int c = 0; c < table.headers().size(); c++) {
                if (c == 0) head.getCell(0).setText(table.headers().get(0));
                else head.addNewTableCell().setText(table.headers().get(c));
            }
            for (List<String> row : table.rows()) {
                XWPFTableRow tRow = t.createRow();
                for (int c = 0; c < row.size(); c++) tRow.getCell(c).setText(row.get(c));
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando Word", e);
        }
    }

    private byte[] renderPdf(ReportTable table) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph title = new Paragraph(safe(table.title()), titleFont);
            title.setSpacingAfter(12f);
            doc.add(title);

            PdfPTable t = new PdfPTable(table.headers().size());
            t.setWidthPercentage(100);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            for (String h : table.headers()) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                t.addCell(cell);
            }
            for (List<String> row : table.rows()) {
                for (String c : row) t.addCell(new Paragraph(c));
            }
            doc.add(t);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private record Row(String label, Map<String, Double> values) {}

    private String resolvePolicyId(String policyName) {
        if (policyName == null || policyName.isBlank()) return null;
        return policyRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(policyName.trim()))
                .map(BusinessPolicy::getId).findFirst().orElse(null);
    }

    private static boolean inRange(LocalDateTime ts, LocalDateTime from, LocalDateTime to) {
        if (ts == null) return from == null && to == null;
        if (from != null && ts.isBefore(from)) return false;
        return to == null || !ts.isAfter(to);
    }

    private static LocalDateTime parseDate(String s, boolean startOfDay) {
        if (s == null || s.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(s.trim());
            return startOfDay ? d.atStartOfDay() : d.atTime(23, 59, 59);
        } catch (Exception e) {
            return null;
        }
    }

    private static InstanceStatus parseInstanceStatus(String s) {
        if (s == null) return null;
        try { return InstanceStatus.valueOf(s); } catch (Exception e) { return null; }
    }

    private static TaskStatus parseTaskStatus(String s) {
        if (s == null) return null;
        try { return TaskStatus.valueOf(s); } catch (Exception e) { return null; }
    }

    private static String groupLabel(String groupBy) {
        return switch (nullToEmpty(groupBy)) {
            case "policy" -> "Política";
            case "status" -> "Estado";
            case "month" -> "Mes";
            case "client" -> "Cliente";
            case "department" -> "Departamento";
            case "user" -> "Usuario";
            case "node" -> "Nodo";
            default -> "Total";
        };
    }

    private static String metricLabel(String metric) {
        return switch (metric) {
            case "count" -> "Cantidad";
            case "avgDurationHours" -> "Duración prom. (h)";
            case "completionRate" -> "% Completados";
            case "cancelledRate" -> "% Cancelados";
            case "avgWorkHours" -> "Trabajo prom. (h)";
            case "instanceCount" -> "# Trámites";
            case "abandonmentRate" -> "% Abandono";
            default -> metric;
        };
    }

    private static String formatMetric(String metric, double v) {
        if (metric.endsWith("Rate")) return String.format(Locale.US, "%.1f%%", v);
        if (metric.endsWith("Hours")) return String.format(Locale.US, "%.2f", v);
        return String.valueOf(Math.round(v));
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
