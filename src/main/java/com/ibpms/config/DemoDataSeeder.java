package com.ibpms.config;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.Department;
import com.ibpms.domain.DocumentRequirement;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.User;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.PolicyStatus;
import com.ibpms.domain.enums.SystemRole;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.repository.DocumentAuditLogRepository;
import com.ibpms.repository.ProcessDocumentRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates a COHERENT demo dataset for KPI / analytics demonstration:
 * 3 active business policies (+1 draft), realistic completed/active/cancelled
 * process instances and tasks with believable timestamps.
 *
 * <p><strong>Activation:</strong> set {@code ibpms.demo-seed=true} (default false).
 * When enabled, it WIPES policies, instances, tasks and documents, then reseeds.
 * Run once with the flag on, then set it back to false so your own test data
 * is not erased on the next restart. Users and departments are preserved
 * (employees get a department assigned).
 *
 * <p>Departments are referenced by INDEX into whatever departments actually exist
 * in the DB (not by hardcoded name), so the seeder is robust to any DB state.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final BusinessPolicyRepository policyRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final ActivityTaskRepository taskRepository;
    private final ProcessDocumentRepository documentRepository;
    private final DocumentAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    private final boolean demoSeedEnabled;

    private final Random rnd = new Random(42); // fixed seed → reproducible dataset

    public DemoDataSeeder(BusinessPolicyRepository policyRepository,
                          ProcessInstanceRepository instanceRepository,
                          ActivityTaskRepository taskRepository,
                          ProcessDocumentRepository documentRepository,
                          DocumentAuditLogRepository auditLogRepository,
                          UserRepository userRepository,
                          DepartmentRepository departmentRepository,
                          @Value("${ibpms.demo-seed:false}") boolean demoSeedEnabled) {
        this.policyRepository = policyRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!demoSeedEnabled) {
            return;
        }
        System.out.println("[DEMO-SEED] ibpms.demo-seed=true → wiping and regenerating demo data...");

        List<Department> departments = departmentRepository.findAll();
        if (departments.isEmpty()) {
            System.out.println("[DEMO-SEED] Aborted — no departments found. Seed departments first.");
            return;
        }

        wipe();
        assignDepartmentsToEmployees(departments);
        Map<String, List<User>> employeesByDept = employeesByDepartment();
        List<User> clients = usersByRole(SystemRole.CLIENT);
        String adminId = usersByRole(SystemRole.ADMIN_DESIGNER).stream()
                .map(User::getId).findFirst().orElse("system");

        if (employeesByDept.isEmpty() || clients.isEmpty()) {
            System.out.println("[DEMO-SEED] Aborted — no employees/clients found. Seed users first.");
            return;
        }

        List<BusinessPolicy> policies = seedPolicies(departments, adminId);

        int totalInstances = 0;
        for (BusinessPolicy policy : policies) {
            if (policy.getStatus() != PolicyStatus.ACTIVE) continue;
            totalInstances += generateInstancesFor(policy, employeesByDept, clients);
        }

        System.out.println("[DEMO-SEED] Done. Policies: " + policies.size()
                + ", instances: " + totalInstances);
        System.out.println("[DEMO-SEED] Remember to set ibpms.demo-seed=false to keep your test data.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    private void wipe() {
        taskRepository.deleteAll();
        instanceRepository.deleteAll();
        documentRepository.deleteAll();
        auditLogRepository.deleteAll();
        policyRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Users / departments
    // ─────────────────────────────────────────────────────────────────────────

    /** Assigns each EMPLOYEE to a department (round-robin over real departments). */
    private void assignDepartmentsToEmployees(List<Department> departments) {
        List<User> employees = usersByRole(SystemRole.EMPLOYEE);
        if (departments.isEmpty() || employees.isEmpty()) return;

        for (int i = 0; i < employees.size(); i++) {
            Department dept = departments.get(i % departments.size());
            employees.get(i).setDepartmentId(dept.id());
        }
        userRepository.saveAll(employees);
    }

    private Map<String, List<User>> employeesByDepartment() {
        return usersByRole(SystemRole.EMPLOYEE).stream()
                .filter(u -> u.getDepartmentId() != null)
                .collect(Collectors.groupingBy(User::getDepartmentId));
    }

    private List<User> usersByRole(SystemRole role) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == role)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Policies
    // ─────────────────────────────────────────────────────────────────────────

    /** A single ACTION step. {@code deptIndex} is resolved against the real department list. */
    private record Step(String label, int deptIndex, long slaSeconds,
                        List<Map<String, Object>> formFields) {}

    /** Shorthand to build a form field map matching the Angular FormField interface. */
    private static Map<String, Object> field(String id, String type, String label,
                                              boolean required, List<String> options) {
        Map<String, Object> f = new HashMap<>();
        f.put("id", id);
        f.put("type", type);
        f.put("label", label);
        f.put("required", required);
        f.put("options", options != null ? options : List.of());
        return f;
    }

    private List<BusinessPolicy> seedPolicies(List<Department> departments, String adminId) {
        List<BusinessPolicy> policies = new ArrayList<>();

        // 1) Solicitud de Crédito Personal (ACTIVE)
        policies.add(buildPolicy(
                "Solicitud de Credito Personal",
                "Evaluacion y otorgamiento de creditos personales a clientes.",
                List.of("credito", "prestamo", "financiamiento"),
                List.of(
                    new Step("Registrar solicitud", 0, 3600, List.of(
                        field("nombre_solicitante", "TEXT",   "Nombre completo del solicitante", true,  null),
                        field("monto_solicitado",   "NUMBER", "Monto solicitado (USD)",          true,  null),
                        field("plazo_meses",        "SELECT", "Plazo en meses",                  true,
                              List.of("12", "24", "36", "48", "60")),
                        field("proposito",          "TEXTAREA","Propósito del crédito",          true,  null)
                    )),
                    new Step("Evaluar viabilidad", 1, 14400, List.of(
                        field("score_crediticio",   "NUMBER", "Score crediticio obtenido",       true,  null),
                        field("resultado",          "SELECT", "Resultado de evaluación",         true,
                              List.of("APROBADO", "APROBADO_CON_CONDICIONES", "RECHAZADO")),
                        field("observaciones",      "TEXTAREA","Observaciones del analista",     false, null)
                    )),
                    new Step("Revision legal", 2, 28800, List.of(
                        field("conformidad_legal",  "SELECT", "Conformidad legal",               true,
                              List.of("CONFORME", "NO_CONFORME", "PENDIENTE_SUBSANACION")),
                        field("numero_contrato",    "TEXT",   "Número de contrato generado",    true,  null),
                        field("firma_cliente",      "SIGNATURE","Firma del cliente",             true,  null)
                    )),
                    new Step("Desembolso", 3, 7200, List.of(
                        field("monto_desembolsado", "NUMBER", "Monto desembolsado (USD)",        true,  null),
                        field("numero_cuenta",      "TEXT",   "Número de cuenta destino",        true,  null),
                        field("fecha_desembolso",   "DATE",   "Fecha de desembolso",             true,  null)
                    ))
                ),
                List.of(
                    docReq("Cedula de identidad", "Documento de identidad vigente",
                           List.of("application/pdf", "image/jpeg", "image/png"), true),
                    docReq("Comprobante de ingresos", "Ultimas 3 boletas de pago",
                           List.of("application/pdf"), true)
                ),
                departments, adminId, PolicyStatus.ACTIVE));

        // 2) Contratación de Servicio (ACTIVE)
        policies.add(buildPolicy(
                "Contratacion de Servicio de Internet",
                "Alta de un nuevo servicio de internet e instalacion de equipo.",
                List.of("servicio", "contratacion", "instalacion", "internet"),
                List.of(
                    new Step("Registrar contratacion", 0, 1800, List.of(
                        field("plan_seleccionado",  "SELECT", "Plan de internet",                true,
                              List.of("BASICO_50MB", "ESTANDAR_100MB", "PREMIUM_300MB", "EMPRESARIAL_1GB")),
                        field("direccion",          "TEXT",   "Dirección de instalación",       true,  null),
                        field("telefono_contacto",  "TEXT",   "Teléfono de contacto",           true,  null)
                    )),
                    new Step("Verificar cobertura tecnica", 1, 7200, List.of(
                        field("cobertura",          "SELECT", "Cobertura disponible",            true,
                              List.of("DISPONIBLE", "NO_DISPONIBLE", "PARCIAL")),
                        field("velocidad_maxima",   "NUMBER", "Velocidad máxima disponible (MB)",true, null),
                        field("observaciones",      "TEXTAREA","Observaciones técnicas",         false, null)
                    )),
                    new Step("Asignar equipo", 4, 10800, List.of(
                        field("numero_serie_router","TEXT",   "Número de serie del router",      true,  null),
                        field("modelo_router",      "TEXT",   "Modelo del equipo asignado",      true,  null),
                        field("fecha_instalacion",  "DATE",   "Fecha programada de instalación", true,  null)
                    )),
                    new Step("Generar factura", 3, 3600, List.of(
                        field("monto_total",        "NUMBER", "Monto total (USD)",               true,  null),
                        field("metodo_pago",        "SELECT", "Método de pago",                  true,
                              List.of("EFECTIVO", "TRANSFERENCIA", "TARJETA_CREDITO", "TARJETA_DEBITO")),
                        field("numero_factura",     "TEXT",   "Número de factura emitida",       true,  null)
                    ))
                ),
                List.of(),
                departments, adminId, PolicyStatus.ACTIVE));

        // 3) Reclamo de Garantía (ACTIVE)
        policies.add(buildPolicy(
                "Reclamo de Garantia",
                "Gestion de reclamos de garantia y reemplazo de equipos.",
                List.of("garantia", "reclamo", "reparacion", "soporte"),
                List.of(
                    new Step("Registrar reclamo", 0, 1800, List.of(
                        field("descripcion_problema","TEXTAREA","Descripción del problema",      true,  null),
                        field("numero_serie",        "TEXT",   "Número de serie del equipo",     true,  null),
                        field("fecha_compra",        "DATE",   "Fecha de compra del equipo",     true,  null)
                    )),
                    new Step("Diagnostico tecnico", 1, 21600, List.of(
                        field("causa_falla",        "SELECT", "Causa de la falla",               true,
                              List.of("FALLA_HARDWARE", "FALLA_SOFTWARE", "DANO_FISICO", "USO_INADECUADO", "DEFECTO_FABRICA")),
                        field("diagnostico",        "TEXTAREA","Diagnóstico detallado",          true,  null),
                        field("requiere_reemplazo", "SELECT", "¿Requiere reemplazo?",            true,
                              List.of("SI", "NO", "REPARACION_EN_SITIO"))
                    )),
                    new Step("Reemplazo de equipo", 4, 14400, List.of(
                        field("numero_serie_nuevo", "TEXT",   "N/S del equipo de reemplazo",     true,  null),
                        field("modelo_reemplazo",   "TEXT",   "Modelo del equipo de reemplazo",  true,  null),
                        field("fecha_entrega",      "DATE",   "Fecha de entrega",                true,  null),
                        field("firma_recepcion",    "SIGNATURE","Firma de recepción del cliente",true,  null)
                    ))
                ),
                List.of(
                    docReq("Factura de compra", "Comprobante original de la compra",
                           List.of("application/pdf", "image/jpeg"), true)
                ),
                departments, adminId, PolicyStatus.ACTIVE));

        // 4) Apertura de Cuenta (DRAFT)
        policies.add(buildPolicy(
                "Apertura de Cuenta de Ahorros",
                "Proceso de apertura de cuenta de ahorros (en diseno).",
                List.of("cuenta", "ahorro", "banca"),
                List.of(
                    new Step("Registrar datos del cliente", 0, 1800, List.of(
                        field("nombres",            "TEXT",   "Nombres completos",               true,  null),
                        field("tipo_cuenta",        "SELECT", "Tipo de cuenta",                  true,
                              List.of("AHORRO_BASICA", "AHORRO_PLUS", "AHORRO_EMPRESARIAL")),
                        field("deposito_inicial",   "NUMBER", "Depósito inicial (USD)",          true,  null)
                    )),
                    new Step("Validacion legal", 2, 7200, List.of(
                        field("conformidad",        "SELECT", "Resultado de validación",         true,
                              List.of("APROBADO", "RECHAZADO", "DOCUMENTACION_INCOMPLETA")),
                        field("numero_cuenta",      "TEXT",   "Número de cuenta asignado",       false, null),
                        field("observaciones",      "TEXTAREA","Observaciones",                  false, null)
                    ))
                ),
                List.of(),
                departments, adminId, PolicyStatus.DRAFT));

        policyRepository.saveAll(policies);
        return policies;
    }

    private BusinessPolicy buildPolicy(String name, String description, List<String> tags,
                                       List<Step> steps, List<DocumentRequirement> docs,
                                       List<Department> departments, String adminId,
                                       PolicyStatus status) {
        BusinessPolicy policy = new BusinessPolicy();
        policy.setName(name);
        policy.setDescription(description);
        policy.setCreatedBy(adminId);
        policy.setStatus(status);
        policy.setTags(tags);
        policy.setDocumentRequirements(docs);
        policy.setCreatedAt(LocalDateTime.now().minusDays(40));
        policy.setUpdatedAt(LocalDateTime.now().minusDays(5));

        List<ActivityPartition> partitions = new ArrayList<>();
        List<ActivityNode> nodes = new ArrayList<>();
        List<ControlFlow> flows = new ArrayList<>();

        // INITIAL node
        String initialId = "node_initial";
        nodes.add(node(initialId, "Inicio", null, NodeType.INITIAL_NODE, null));

        // Build one partition per distinct department index used in the steps
        Map<Integer, String> partitionIdByDeptIndex = new HashMap<>();
        for (Step s : steps) {
            partitionIdByDeptIndex.computeIfAbsent(s.deptIndex(), idx -> {
                Department dept = departments.get(idx % departments.size());
                String pid = "lane_" + partitionIdByDeptIndex.size();
                partitions.add(new ActivityPartition(pid, dept.name(), dept.id()));
                return pid;
            });
        }

        // ACTION nodes
        String prevId = initialId;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String nodeId = "node_action_" + i;
            Map<String, String> meta = new HashMap<>();
            meta.put("slaSeconds", String.valueOf(s.slaSeconds()));
            Map<String, Object> formSchema = s.formFields() != null && !s.formFields().isEmpty()
                    ? Map.of("fields", s.formFields())
                    : new HashMap<>();
            nodes.add(nodeWithForm(nodeId, s.label(), partitionIdByDeptIndex.get(s.deptIndex()),
                    NodeType.ACTION, meta, formSchema));
            flows.add(flow(prevId, nodeId));
            prevId = nodeId;
        }

        // FINAL node
        String finalId = "node_final";
        nodes.add(node(finalId, "Fin", null, NodeType.ACTIVITY_FINAL, null));
        flows.add(flow(prevId, finalId));

        policy.setPartitions(partitions);
        policy.setNodes(nodes);
        policy.setFlows(flows);
        policy.setBpmnXml(generateBpmnXml(name, partitions, nodes, flows));
        return policy;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BPMN XML generation (so seeded policies render in the designer)
    // ─────────────────────────────────────────────────────────────────────────

    private String generateBpmnXml(String policyName, List<ActivityPartition> partitions,
                                   List<ActivityNode> nodes, List<ControlFlow> flows) {
        final int PX = 160, PY = 80, LANE_H = 160, COL_W = 180;
        int numLanes = Math.max(1, partitions.size());

        Map<String, Integer> laneIndexByPartition = new HashMap<>();
        for (int i = 0; i < partitions.size(); i++) {
            laneIndexByPartition.put(partitions.get(i).getId(), i);
        }

        // Linear column layout in node declaration order (initial → actions → final)
        Map<String, Integer> colByNode = new HashMap<>();
        int col = 0;
        for (ActivityNode n : nodes) colByNode.put(n.getId(), col++);
        int totalCols = nodes.size();

        int participantW = 60 + totalCols * COL_W + 20;
        int participantH = numLanes * LANE_H;

        Map<String, int[]> geo = new HashMap<>(); // x, y, w, h
        for (ActivityNode n : nodes) {
            int c = colByNode.get(n.getId());
            int laneIdx = n.getPartitionId() != null
                    ? laneIndexByPartition.getOrDefault(n.getPartitionId(), 0) : 0;
            int laneTop = PY + laneIdx * LANE_H;
            int slotX = PX + 60 + c * COL_W;
            if (n.getType() == NodeType.ACTION) {
                geo.put(n.getId(), new int[]{slotX, laneTop + (LANE_H - 80) / 2, 100, 80});
            } else {
                geo.put(n.getId(), new int[]{slotX + 32, laneTop + (LANE_H - 36) / 2, 36, 36});
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ")
          .append("xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" ")
          .append("xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" ")
          .append("xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" ")
          .append("id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n");

        sb.append("  <bpmn:collaboration id=\"Collaboration_1\">\n");
        sb.append("    <bpmn:participant id=\"Participant_1\" name=\"").append(xml(policyName))
          .append("\" processRef=\"Process_1\" />\n");
        sb.append("  </bpmn:collaboration>\n");

        sb.append("  <bpmn:process id=\"Process_1\" isExecutable=\"false\">\n");
        sb.append("    <bpmn:laneSet id=\"LaneSet_1\">\n");
        for (int i = 0; i < partitions.size(); i++) {
            ActivityPartition p = partitions.get(i);
            sb.append("      <bpmn:lane id=\"").append(p.getId()).append("\" name=\"")
              .append(xml(p.getLabel())).append("\">\n");
            for (ActivityNode n : nodes) {
                boolean inLane = (n.getPartitionId() != null && n.getPartitionId().equals(p.getId()))
                        || (n.getPartitionId() == null && i == 0);
                if (inLane) {
                    sb.append("        <bpmn:flowNodeRef>").append(n.getId())
                      .append("</bpmn:flowNodeRef>\n");
                }
            }
            sb.append("      </bpmn:lane>\n");
        }
        sb.append("    </bpmn:laneSet>\n");

        for (ActivityNode n : nodes) {
            String inc = flowRefs(n.getId(), flows, false);
            String out = flowRefs(n.getId(), flows, true);
            switch (n.getType()) {
                case INITIAL_NODE -> sb.append("    <bpmn:startEvent id=\"").append(n.getId())
                        .append("\" name=\"").append(xml(n.getLabel())).append("\">")
                        .append(out).append("</bpmn:startEvent>\n");
                case ACTION -> sb.append("    <bpmn:task id=\"").append(n.getId())
                        .append("\" name=\"").append(xml(n.getLabel())).append("\">")
                        .append(inc).append(out).append("</bpmn:task>\n");
                case ACTIVITY_FINAL -> sb.append("    <bpmn:endEvent id=\"").append(n.getId())
                        .append("\" name=\"").append(xml(n.getLabel())).append("\">")
                        .append(inc).append("</bpmn:endEvent>\n");
                default -> { }
            }
        }

        for (ControlFlow f : flows) {
            sb.append("    <bpmn:sequenceFlow id=\"").append(f.getId())
              .append("\" sourceRef=\"").append(f.getSourceNodeId())
              .append("\" targetRef=\"").append(f.getTargetNodeId()).append("\" />\n");
        }
        sb.append("  </bpmn:process>\n");

        sb.append("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n");
        sb.append("    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Collaboration_1\">\n");
        sb.append("      <bpmndi:BPMNShape id=\"Participant_1_di\" bpmnElement=\"Participant_1\" isHorizontal=\"true\">\n");
        sb.append("        <dc:Bounds x=\"").append(PX).append("\" y=\"").append(PY)
          .append("\" width=\"").append(participantW).append("\" height=\"").append(participantH).append("\" />\n");
        sb.append("      </bpmndi:BPMNShape>\n");
        for (int i = 0; i < partitions.size(); i++) {
            ActivityPartition p = partitions.get(i);
            int laneY = PY + i * LANE_H;
            sb.append("      <bpmndi:BPMNShape id=\"").append(p.getId())
              .append("_di\" bpmnElement=\"").append(p.getId()).append("\" isHorizontal=\"true\">\n");
            sb.append("        <dc:Bounds x=\"").append(PX + 30).append("\" y=\"").append(laneY)
              .append("\" width=\"").append(participantW - 30).append("\" height=\"").append(LANE_H).append("\" />\n");
            sb.append("      </bpmndi:BPMNShape>\n");
        }
        for (ActivityNode n : nodes) {
            int[] g = geo.get(n.getId());
            sb.append("      <bpmndi:BPMNShape id=\"").append(n.getId())
              .append("_di\" bpmnElement=\"").append(n.getId()).append("\">\n");
            sb.append("        <dc:Bounds x=\"").append(g[0]).append("\" y=\"").append(g[1])
              .append("\" width=\"").append(g[2]).append("\" height=\"").append(g[3]).append("\" />\n");
            sb.append("      </bpmndi:BPMNShape>\n");
        }
        for (ControlFlow f : flows) {
            int[] s = geo.get(f.getSourceNodeId());
            int[] t = geo.get(f.getTargetNodeId());
            if (s == null || t == null) continue;
            int sx = s[0] + s[2], sy = s[1] + s[3] / 2;
            int tx = t[0], ty = t[1] + t[3] / 2;
            sb.append("      <bpmndi:BPMNEdge id=\"").append(f.getId())
              .append("_di\" bpmnElement=\"").append(f.getId()).append("\">\n");
            sb.append("        <di:waypoint x=\"").append(sx).append("\" y=\"").append(sy).append("\" />\n");
            sb.append("        <di:waypoint x=\"").append(tx).append("\" y=\"").append(ty).append("\" />\n");
            sb.append("      </bpmndi:BPMNEdge>\n");
        }
        sb.append("    </bpmndi:BPMNPlane>\n");
        sb.append("  </bpmndi:BPMNDiagram>\n");
        sb.append("</bpmn:definitions>");
        return sb.toString();
    }

    private String flowRefs(String nodeId, List<ControlFlow> flows, boolean outgoing) {
        StringBuilder sb = new StringBuilder();
        for (ControlFlow f : flows) {
            String match = outgoing ? f.getSourceNodeId() : f.getTargetNodeId();
            if (match.equals(nodeId)) {
                sb.append(outgoing ? "<bpmn:outgoing>" : "<bpmn:incoming>")
                  .append(f.getId())
                  .append(outgoing ? "</bpmn:outgoing>" : "</bpmn:incoming>");
            }
        }
        return sb.toString();
    }

    private String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private ActivityNode node(String id, String label, String partitionId,
                              NodeType type, Map<String, String> metadata) {
        return nodeWithForm(id, label, partitionId, type, metadata, new HashMap<>());
    }

    private ActivityNode nodeWithForm(String id, String label, String partitionId,
                                      NodeType type, Map<String, String> metadata,
                                      Map<String, Object> formSchema) {
        ActivityNode n = new ActivityNode();
        n.setId(id);
        n.setLabel(label);
        n.setPartitionId(partitionId);
        n.setType(type);
        n.setFormSchema(formSchema);
        n.setMetadata(metadata != null ? metadata : new HashMap<>());
        return n;
    }

    private ControlFlow flow(String source, String target) {
        return new ControlFlow("flow_" + source + "_" + target, source, target, null);
    }

    private DocumentRequirement docReq(String name, String desc, List<String> mimes, boolean mandatory) {
        DocumentRequirement r = new DocumentRequirement();
        r.setId(UUID.randomUUID().toString());
        r.setName(name);
        r.setDescription(desc);
        r.setAllowedMimeTypes(mimes);
        r.setMandatory(mandatory);
        r.setUploadStage("PROCESS_START");
        r.setUploaderRole("ANY");
        r.setMaxSizeBytes(5_242_880L);
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instances + tasks (the data that powers the KPIs)
    // ─────────────────────────────────────────────────────────────────────────

    private record ActionRef(String nodeId, String departmentId, long slaSeconds) {}

    private int generateInstancesFor(BusinessPolicy policy,
                                     Map<String, List<User>> employeesByDept,
                                     List<User> clients) {
        // Resolve department per partition (departmentId is guaranteed non-null here)
        Map<String, String> deptByPartition = new HashMap<>();
        for (ActivityPartition p : policy.getPartitions()) {
            deptByPartition.put(p.getId(), p.getDepartmentId());
        }

        List<ActionRef> actions = policy.getNodes().stream()
                .filter(n -> n.getType() == NodeType.ACTION)
                .map(n -> new ActionRef(
                        n.getId(),
                        deptByPartition.get(n.getPartitionId()),
                        parseSla(n.getMetadata())))
                .toList();

        if (actions.isEmpty()) return 0;

        String finalNodeId = policy.getNodes().stream()
                .filter(n -> n.getType() == NodeType.ACTIVITY_FINAL)
                .map(ActivityNode::getId).findFirst().orElse("node_final");

        int count = 18 + rnd.nextInt(8); // 18–25 instances per policy
        List<ProcessInstance> instances = new ArrayList<>();
        List<ActivityTask> tasks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDateTime startedAt = LocalDateTime.now()
                    .minusDays(rnd.nextInt(28))
                    .minusHours(rnd.nextInt(24))
                    .minusMinutes(rnd.nextInt(60));

            int roll = rnd.nextInt(100);
            InstanceStatus status = roll < 60 ? InstanceStatus.COMPLETED
                    : roll < 85 ? InstanceStatus.ACTIVE : InstanceStatus.CANCELLED;

            User client = clients.get(rnd.nextInt(clients.size()));

            ProcessInstance instance = new ProcessInstance();
            instance.setBusinessPolicyId(policy.getId());
            instance.setInitiatedBy(client.getId());
            instance.setClientId(client.getId());
            instance.setContextData(new HashMap<>());
            instance.setStartedAt(startedAt);
            instance = instanceRepository.save(instance);

            int completedSteps = switch (status) {
                case COMPLETED -> actions.size();
                case ACTIVE -> Math.max(0, rnd.nextInt(actions.size()));
                case CANCELLED -> 1 + rnd.nextInt(Math.max(1, actions.size() - 1));
            };

            LocalDateTime cursor = startedAt;
            String currentNodeId = actions.get(0).nodeId();

            for (int s = 0; s < actions.size(); s++) {
                ActionRef action = actions.get(s);
                List<User> deptEmployees = action.departmentId() == null ? List.of()
                        : employeesByDept.getOrDefault(action.departmentId(), List.of());
                if (deptEmployees.isEmpty()) continue;
                User emp = deptEmployees.get(rnd.nextInt(deptEmployees.size()));

                ActivityTask task = new ActivityTask();
                task.setProcessInstanceId(instance.getId());
                task.setNodeId(action.nodeId());
                task.setAssignedDepartmentId(action.departmentId());
                task.setFormData(new HashMap<>());
                task.setAssignedAt(cursor);

                if (s < completedSteps) {
                    long waitSec = 300 + rnd.nextInt(7200);
                    LocalDateTime taskStarted = cursor.plusSeconds(waitSec);
                    double empFactor = employeeSpeedFactor(emp.getId());
                    double variance = 0.5 + rnd.nextDouble() * 1.3;
                    long workSec = Math.max(60, (long) (action.slaSeconds() * variance * empFactor));
                    LocalDateTime taskCompleted = taskStarted.plusSeconds(workSec);

                    task.setAssignedUserId(emp.getId());
                    task.setStatus(TaskStatus.COMPLETED);
                    task.setStartedAt(taskStarted);
                    task.setCompletedAt(taskCompleted);
                    tasks.add(task);

                    cursor = taskCompleted.plusMinutes(5 + rnd.nextInt(120));
                    currentNodeId = (s + 1 < actions.size()) ? actions.get(s + 1).nodeId() : finalNodeId;
                } else if (s == completedSteps && status == InstanceStatus.ACTIVE) {
                    boolean claimed = rnd.nextBoolean();
                    if (claimed) {
                        task.setAssignedUserId(emp.getId());
                        task.setStatus(TaskStatus.IN_PROGRESS);
                        task.setStartedAt(cursor.plusSeconds(300 + rnd.nextInt(3600)));
                    } else {
                        task.setStatus(TaskStatus.PENDING);
                    }
                    tasks.add(task);
                    currentNodeId = action.nodeId();
                    break;
                } else {
                    break;
                }
            }

            instance.setCurrentNodeId(status == InstanceStatus.COMPLETED ? finalNodeId : currentNodeId);
            instance.setStatus(status);
            if (status == InstanceStatus.COMPLETED) {
                instance.setCompletedAt(cursor);
            } else if (status == InstanceStatus.CANCELLED) {
                instance.setCompletedAt(cursor.plusHours(1 + rnd.nextInt(48)));
            }
            instances.add(instance);
        }

        instanceRepository.saveAll(instances);
        taskRepository.saveAll(tasks);
        return instances.size();
    }

    private long parseSla(Map<String, String> metadata) {
        if (metadata == null) return 3600;
        try {
            return Long.parseLong(metadata.getOrDefault("slaSeconds", "3600"));
        } catch (NumberFormatException e) {
            return 3600;
        }
    }

    /**
     * Deterministic speed factor per employee so performance levels are stable:
     * ~30% fast (GOOD), ~50% average, ~20% slow (POOR).
     */
    private double employeeSpeedFactor(String userId) {
        int bucket = Math.floorMod(userId.hashCode(), 10);
        if (bucket < 3) return 0.6 + rnd.nextDouble() * 0.15;
        if (bucket < 8) return 0.9 + rnd.nextDouble() * 0.25;
        return 1.4 + rnd.nextDouble() * 0.4;
    }
}
