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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates a COHERENT demo dataset for ELECSUR — Empresa Eléctrica.
 *
 * <p>Policies modelled on real electricity-company workflows: meter installation,
 * service reconnection, billing complaints and capacity upgrades. Employees are
 * assigned to their specific departments by {@link DataSeeder}; this seeder only
 * generates policies and process instances on top of that foundation.
 *
 * <p><strong>Activation:</strong> set {@code ibpms.demo-seed=true} (default false).
 * When enabled it WIPES policies, instances, tasks and documents, then reseeds.
 * Run once with the flag on, then set it back to false so your own test data is
 * not erased on the next restart. Users and departments are preserved.
 *
 * <p>Department references use canonical names (see {@link DataSeeder#seedDepartments})
 * so the seeder is robust to any MongoDB document-order.
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

    /** Fixed seed → reproducible dataset across restarts. */
    private final Random rnd = new Random(42);

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
        if (!demoSeedEnabled) return;
        System.out.println("[DEMO-SEED] ibpms.demo-seed=true → wiping and regenerating demo data…");

        // Index departments by canonical name for O(1) look-up
        Map<String, Department> deptByName = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::name, d -> d));

        if (deptByName.isEmpty()) {
            System.out.println("[DEMO-SEED] Aborted — no departments found. Seed departments first.");
            return;
        }

        Map<String, List<User>> employeesByDept = employeesByDepartment();
        List<User> clients = usersByRole(SystemRole.CLIENT);
        String adminId = usersByRole(SystemRole.ADMIN_DESIGNER).stream()
                .map(User::getId).findFirst().orElse("system");

        if (employeesByDept.isEmpty() || clients.isEmpty()) {
            System.out.println("[DEMO-SEED] Aborted — no employees/clients found. Seed users first.");
            return;
        }

        wipe();

        List<BusinessPolicy> policies = seedPolicies(deptByName, adminId);

        int totalInstances = 0;
        for (BusinessPolicy policy : policies) {
            if (policy.getStatus() != PolicyStatus.ACTIVE) continue;
            totalInstances += generateInstancesFor(policy, employeesByDept, clients);
        }

        System.out.println("[DEMO-SEED] Done. Políticas: " + policies.size()
                + ", instancias: " + totalInstances);
        System.out.println("[DEMO-SEED] Remember to set ibpms.demo-seed=false to keep your data.");
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
    // Users helpers
    // ─────────────────────────────────────────────────────────────────────────

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
    // Policy definitions — ELECSUR electricity company
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single ACTION step.
     *
     * @param label      Human-readable step name shown in the UI.
     * @param deptName   Canonical department name (must match stored value in MongoDB).
     * @param slaSeconds Target SLA for this step in seconds.
     * @param formFields Dynamic form fields for task completion.
     */
    private record Step(String label, String deptName, long slaSeconds,
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

    private List<BusinessPolicy> seedPolicies(Map<String, Department> deptByName, String adminId) {
        List<BusinessPolicy> policies = new ArrayList<>();

        // ── 1) Instalación de Nuevo Medidor (ACTIVE) ─────────────────────────
        policies.add(buildPolicy(
                "Instalacion de Nuevo Medidor",
                "Proceso completo para instalar un medidor eléctrico en un nuevo punto de suministro.",
                List.of("medidor", "instalacion", "nuevo-servicio", "electricidad"),
                List.of(
                    new Step("Registrar Solicitud", "Atencion Al Cliente", 3_600, List.of(
                        field("nombre_titular",    "TEXT",    "Nombre del titular",              true,  null),
                        field("cedula",            "TEXT",    "Número de cédula",                true,  null),
                        field("direccion",         "TEXTAREA","Dirección del inmueble",          true,  null),
                        field("telefono",          "TEXT",    "Teléfono de contacto",            true,  null),
                        field("tipo_suministro",   "SELECT",  "Tipo de suministro",              true,
                              List.of("RESIDENCIAL", "COMERCIAL", "INDUSTRIAL"))
                    )),
                    new Step("Inspeccion de Factibilidad", "Departamento Tecnico", 14_400, List.of(
                        field("factibilidad",      "SELECT",  "Resultado de inspección",         true,
                              List.of("FACTIBLE", "NO_FACTIBLE", "FACTIBLE_CON_ADECUACIONES")),
                        field("distancia_red",     "NUMBER",  "Distancia a red existente (m)",   true,  null),
                        field("observaciones_tec", "TEXTAREA","Observaciones técnicas",          false, null),
                        field("fecha_inspeccion",  "DATE",    "Fecha de inspección",             true,  null)
                    )),
                    new Step("Firma de Contrato", "Departamento Legal", 28_800, List.of(
                        field("numero_contrato",   "TEXT",    "Número de contrato generado",     true,  null),
                        field("tipo_tarifa",       "SELECT",  "Tarifa aplicable",                true,
                              List.of("TARIFA_1", "TARIFA_2", "TARIFA_3", "TARIFA_4")),
                        field("conformidad_legal",  "SELECT",  "Conformidad legal",              true,
                              List.of("CONFORME", "PENDIENTE_SUBSANACION")),
                        field("firma_cliente",     "SIGNATURE","Firma del cliente",              true,  null)
                    )),
                    new Step("Instalacion del Medidor", "Operaciones De Campo", 21_600, List.of(
                        field("numero_serie_medidor","TEXT",  "N/S del medidor instalado",       true,  null),
                        field("tipo_medidor",      "SELECT",  "Tipo de medidor",                 true,
                              List.of("MONOFASICO", "TRIFASICO", "PREPAGO")),
                        field("lectura_inicial",   "NUMBER",  "Lectura inicial (kWh)",           true,  null),
                        field("foto_instalacion",  "TEXT",    "Referencia fotográfica",          false, null)
                    )),
                    new Step("Activacion del Servicio", "Facturacion", 7_200, List.of(
                        field("cuenta_cliente",    "TEXT",    "Número de cuenta eléctrica",      true,  null),
                        field("fecha_activacion",  "DATE",    "Fecha de activación",             true,  null),
                        field("deposito_garantia", "NUMBER",  "Depósito de garantía (USD)",      true,  null),
                        field("metodo_pago",       "SELECT",  "Método de pago",                  true,
                              List.of("EFECTIVO", "TRANSFERENCIA", "TARJETA_DEBITO"))
                    ))
                ),
                List.of(
                    docReq("Cédula de identidad",
                           "Documento de identidad vigente del titular",
                           List.of("application/pdf", "image/jpeg", "image/png"), true),
                    docReq("Título de propiedad o arriendo",
                           "Documento que acredite la tenencia del inmueble",
                           List.of("application/pdf", "image/jpeg"), true),
                    docReq("Plano eléctrico interno",
                           "Plano eléctrico interno del inmueble (opcional para residencial)",
                           List.of("application/pdf"), false)
                ),
                deptByName, adminId, PolicyStatus.ACTIVE));

        // ── 2) Reconexión de Servicio Eléctrico (ACTIVE) ─────────────────────
        policies.add(buildPolicy(
                "Reconexion de Servicio Electrico",
                "Restablecimiento del servicio eléctrico tras suspensión por falta de pago u otras causas.",
                List.of("reconexion", "suspension", "pago", "servicio"),
                List.of(
                    new Step("Registrar Solicitud", "Atencion Al Cliente", 1_800, List.of(
                        field("numero_cuenta",     "TEXT",    "Número de cuenta eléctrica",      true,  null),
                        field("nombre_titular",    "TEXT",    "Nombre del titular",              true,  null),
                        field("motivo_solicitud",  "SELECT",  "Motivo de la solicitud",          true,
                              List.of("PAGO_REALIZADO", "ERROR_ADMINISTRATIVO", "ACUERDO_PAGO")),
                        field("telefono",          "TEXT",    "Teléfono de contacto",            true,  null)
                    )),
                    new Step("Verificar Estado de Cuenta", "Facturacion", 7_200, List.of(
                        field("deuda_pendiente",   "NUMBER",  "Deuda pendiente (USD)",           true,  null),
                        field("estado_cuenta",     "SELECT",  "Estado de cuenta",                true,
                              List.of("SALDADO", "ACUERDO_VIGENTE", "PENDIENTE_PAGO")),
                        field("numero_recibo",     "TEXT",    "Número de recibo de pago",        false, null),
                        field("observaciones",     "TEXTAREA","Observaciones de facturación",    false, null)
                    )),
                    new Step("Ejecutar Reconexion", "Operaciones De Campo", 10_800, List.of(
                        field("tecnico_responsable","TEXT",   "Nombre del técnico asignado",     true,  null),
                        field("fecha_reconexion",  "DATE",    "Fecha de reconexión",             true,  null),
                        field("lectura_actual",    "NUMBER",  "Lectura al momento de reconexión (kWh)", true, null),
                        field("resultado",         "SELECT",  "Resultado de la reconexión",      true,
                              List.of("EXITOSA", "FALLIDA_EQUIPO", "FALLIDA_ACCESO")),
                        field("observaciones",     "TEXTAREA","Observaciones de campo",         false, null)
                    ))
                ),
                List.of(
                    docReq("Comprobante de pago",
                           "Recibo o comprobante bancario del pago realizado",
                           List.of("application/pdf", "image/jpeg", "image/png"), true)
                ),
                deptByName, adminId, PolicyStatus.ACTIVE));

        // ── 3) Reclamo por Facturación Incorrecta (ACTIVE) ───────────────────
        policies.add(buildPolicy(
                "Reclamo por Facturacion Incorrecta",
                "Revisión y corrección de valores erróneos en planillas de consumo eléctrico.",
                List.of("reclamo", "facturacion", "consumo", "planilla"),
                List.of(
                    new Step("Registrar Reclamo", "Atencion Al Cliente", 2_700, List.of(
                        field("numero_cuenta",     "TEXT",    "Número de cuenta eléctrica",      true,  null),
                        field("periodo_reclamo",   "TEXT",    "Período facturado en disputa (MM/YYYY)", true, null),
                        field("valor_facturado",   "NUMBER",  "Valor facturado (USD)",           true,  null),
                        field("valor_estimado",    "NUMBER",  "Valor estimado correcto (USD)",   false, null),
                        field("descripcion",       "TEXTAREA","Descripción del reclamo",         true,  null)
                    )),
                    new Step("Auditoria de Consumo", "Facturacion", 21_600, List.of(
                        field("consumo_promedio",  "NUMBER",  "Consumo promedio últimos 6 meses (kWh)", true, null),
                        field("consumo_facturado", "NUMBER",  "Consumo facturado en disputa (kWh)", true, null),
                        field("variacion",         "NUMBER",  "Variación porcentual (%)",        true,  null),
                        field("causa_variacion",   "SELECT",  "Causa identificada",              true,
                              List.of("ERROR_LECTURA", "FALLA_MEDIDOR", "CONSUMO_REAL", "OTRO")),
                        field("resultado_auditoria","SELECT", "Resultado de auditoría",          true,
                              List.of("PROCEDE_AJUSTE", "NO_PROCEDE", "REQUIERE_INSPECCION"))
                    )),
                    new Step("Resolucion y Ajuste", "Departamento Legal", 14_400, List.of(
                        field("resolucion",        "SELECT",  "Resolución final",                true,
                              List.of("AJUSTE_APROBADO", "RECLAMO_NEGADO", "COMPENSACION_OTORGADA")),
                        field("monto_ajuste",      "NUMBER",  "Monto de ajuste (USD, 0 si no aplica)", true, null),
                        field("numero_nota_credito","TEXT",   "Número de nota de crédito",       false, null),
                        field("observaciones_res", "TEXTAREA","Observaciones de resolución",     false, null)
                    ))
                ),
                List.of(
                    docReq("Planilla en disputa",
                           "Copia de la planilla o factura que se está reclamando",
                           List.of("application/pdf", "image/jpeg", "image/png"), true)
                ),
                deptByName, adminId, PolicyStatus.ACTIVE));

        // ── 4) Ampliación de Capacidad Eléctrica (DRAFT) ─────────────────────
        policies.add(buildPolicy(
                "Ampliacion de Capacidad Electrica",
                "Proceso de incremento de la potencia contratada para clientes comerciales e industriales. (En diseño)",
                List.of("ampliacion", "capacidad", "potencia", "industrial"),
                List.of(
                    new Step("Registrar Solicitud", "Atencion Al Cliente", 1_800, List.of(
                        field("numero_cuenta",     "TEXT",    "Número de cuenta eléctrica",      true,  null),
                        field("capacidad_actual",  "NUMBER",  "Capacidad actual (kVA)",          true,  null),
                        field("capacidad_requerida","NUMBER", "Capacidad requerida (kVA)",        true,  null),
                        field("justificacion",     "TEXTAREA","Justificación de la ampliación",  true,  null)
                    )),
                    new Step("Evaluacion Tecnica", "Departamento Tecnico", 28_800, List.of(
                        field("viabilidad",        "SELECT",  "Viabilidad técnica",              true,
                              List.of("VIABLE", "NO_VIABLE", "VIABLE_CON_OBRAS")),
                        field("costo_estimado",    "NUMBER",  "Costo estimado de obras (USD)",   false, null),
                        field("plazo_estimado",    "TEXT",    "Plazo estimado (semanas)",         false, null),
                        field("informe_tecnico",   "TEXTAREA","Informe técnico detallado",       true,  null)
                    ))
                ),
                List.of(),
                deptByName, adminId, PolicyStatus.DRAFT));

        policyRepository.saveAll(policies);
        return policies;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Policy builder
    // ─────────────────────────────────────────────────────────────────────────

    private BusinessPolicy buildPolicy(String name, String description, List<String> tags,
                                       List<Step> steps, List<DocumentRequirement> docs,
                                       Map<String, Department> deptByName, String adminId,
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

        // Build one partition per distinct department name used in the steps
        // (preserves order of first appearance)
        Map<String, String> partitionIdByDeptName = new LinkedHashMap<>();
        for (Step s : steps) {
            partitionIdByDeptName.computeIfAbsent(s.deptName(), deptName -> {
                Department dept = deptByName.get(deptName);
                if (dept == null) {
                    System.err.println("[DEMO-SEED] ⚠ Department not found: " + deptName);
                    return null;
                }
                String pid = "lane_" + partitionIdByDeptName.size();
                partitions.add(new ActivityPartition(pid, dept.name(), dept.id()));
                return pid;
            });
        }

        // ACTION nodes
        String prevId = initialId;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String partitionId = partitionIdByDeptName.get(s.deptName());
            String nodeId = "node_action_" + i;
            Map<String, String> meta = new HashMap<>();
            meta.put("slaSeconds", String.valueOf(s.slaSeconds()));
            Map<String, Object> formSchema = s.formFields() != null && !s.formFields().isEmpty()
                    ? Map.of("fields", s.formFields())
                    : new HashMap<>();
            nodes.add(nodeWithForm(nodeId, s.label(), partitionId, NodeType.ACTION, meta, formSchema));
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

        Map<String, Integer> colByNode = new HashMap<>();
        int col = 0;
        for (ActivityNode n : nodes) colByNode.put(n.getId(), col++);
        int totalCols = nodes.size();

        int participantW = 60 + totalCols * COL_W + 20;
        int participantH = numLanes * LANE_H;

        Map<String, int[]> geo = new HashMap<>();
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
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            LocalDateTime startedAt = now
                    .minusDays(rnd.nextInt(28))
                    .minusHours(rnd.nextInt(24))
                    .minusMinutes(rnd.nextInt(60));

            // 60% COMPLETED · 25% ACTIVE · 15% CANCELLED
            int roll = rnd.nextInt(100);
            InstanceStatus status = roll < 60 ? InstanceStatus.COMPLETED
                    : roll < 85 ? InstanceStatus.ACTIVE : InstanceStatus.CANCELLED;

            User client = clients.get(rnd.nextInt(clients.size()));

            // Tasks for THIS instance (collected locally so an ACTIVE instance's whole
            // timeline can be shifted to be "recently in progress" before persisting).
            List<ActivityTask> instTasks = new ArrayList<>();

            ProcessInstance instance = new ProcessInstance();
            instance.setBusinessPolicyId(policy.getId());
            instance.setInitiatedBy(client.getId());
            instance.setClientId(client.getId());
            instance.setContextData(new HashMap<>());
            instance.setStartedAt(startedAt);
            instance = instanceRepository.save(instance);

            int completedSteps = switch (status) {
                case COMPLETED -> actions.size();
                case ACTIVE    -> Math.max(0, rnd.nextInt(actions.size()));
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
                    long waitSec = 300 + rnd.nextInt(7_200);
                    LocalDateTime taskStarted = cursor.plusSeconds(waitSec);
                    double empFactor = employeeSpeedFactor(emp.getId());
                    double variance = 0.5 + rnd.nextDouble() * 1.3;
                    long workSec = Math.max(60, (long) (action.slaSeconds() * variance * empFactor));
                    LocalDateTime taskCompleted = taskStarted.plusSeconds(workSec);

                    task.setAssignedUserId(emp.getId());
                    task.setStatus(TaskStatus.COMPLETED);
                    task.setStartedAt(taskStarted);
                    task.setCompletedAt(taskCompleted);
                    instTasks.add(task);

                    cursor = taskCompleted.plusMinutes(5 + rnd.nextInt(120));
                    currentNodeId = (s + 1 < actions.size()) ? actions.get(s + 1).nodeId() : finalNodeId;

                } else if (s == completedSteps && status == InstanceStatus.ACTIVE) {
                    boolean claimed = rnd.nextBoolean();
                    if (claimed) {
                        task.setAssignedUserId(emp.getId());
                        task.setStatus(TaskStatus.IN_PROGRESS);
                        task.setStartedAt(cursor.plusSeconds(300 + rnd.nextInt(3_600)));
                    } else {
                        task.setStatus(TaskStatus.PENDING);
                    }
                    instTasks.add(task);
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
            } else { // ACTIVE: shift the whole timeline so the instance is realistically in-flight.
                // ~75% healthy: last activity was minutes-to-hours ago → elapsed tracks progress.
                // ~25% stuck: last activity 1–5 days ago → elapsed ≫ progress → genuine anomaly.
                boolean stuck = rnd.nextInt(100) < 25;
                long idleGapSec = stuck
                        ? Duration.ofDays(1).plusHours(rnd.nextInt(96)).getSeconds()
                        : 300L + rnd.nextInt(7_200); // 5 min – 2 h
                LocalDateTime targetLast = now.minusSeconds(idleGapSec);
                long shiftSec = Duration.between(cursor, targetLast).getSeconds();
                startedAt = startedAt.plusSeconds(shiftSec);
                instance.setStartedAt(startedAt);
                for (ActivityTask t : instTasks) {
                    if (t.getAssignedAt() != null)  t.setAssignedAt(t.getAssignedAt().plusSeconds(shiftSec));
                    if (t.getStartedAt() != null)   t.setStartedAt(t.getStartedAt().plusSeconds(shiftSec));
                    if (t.getCompletedAt() != null) t.setCompletedAt(t.getCompletedAt().plusSeconds(shiftSec));
                }
            }
            tasks.addAll(instTasks);
            instances.add(instance);
        }

        instanceRepository.saveAll(instances);
        taskRepository.saveAll(tasks);
        return instances.size();
    }

    private long parseSla(Map<String, String> metadata) {
        if (metadata == null) return 3_600;
        try {
            return Long.parseLong(metadata.getOrDefault("slaSeconds", "3600"));
        } catch (NumberFormatException e) {
            return 3_600;
        }
    }

    /**
     * Deterministic speed factor per employee — stable across runs.
     * ~30% fast (bucket 0-2), ~50% average (3-7), ~20% slow (8-9).
     */
    private double employeeSpeedFactor(String userId) {
        int bucket = Math.floorMod(userId.hashCode(), 10);
        if (bucket < 3) return 0.6 + rnd.nextDouble() * 0.15;
        if (bucket < 8) return 0.9 + rnd.nextDouble() * 0.25;
        return 1.4 + rnd.nextDouble() * 0.4;
    }
}
