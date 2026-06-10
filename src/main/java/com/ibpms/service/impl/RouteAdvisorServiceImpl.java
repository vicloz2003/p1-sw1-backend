package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.DocumentRequirement;
import com.ibpms.domain.ProcessDocument;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.DocumentStatus;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.dto.response.RouteAdvisoryResponse;
import com.ibpms.exception.AgentUnavailableException;
import com.ibpms.exception.PolicyNotFoundException;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessDocumentRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.RouteAdvisorService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes a decision node's context from MongoDB and delegates the "best branch" prediction to
 * the ibpms_ml route classifier (RF-3.1). Advisory only — does not drive the engine.
 *
 * <p>Feature derivation is policy-agnostic: {@code docCompleteness} is computed from the real
 * document repository; the remaining numeric signals are read from the instance's
 * {@code contextData} (form data) by generic feature keys, defaulting to neutral when absent.
 * No business names are hard-coded (per project conventions).
 */
@Service
public class RouteAdvisorServiceImpl implements RouteAdvisorService {

    private final ProcessInstanceRepository instanceRepository;
    private final BusinessPolicyRepository policyRepository;
    private final ProcessDocumentRepository documentRepository;
    private final RestClient mlRestClient;

    public RouteAdvisorServiceImpl(ProcessInstanceRepository instanceRepository,
                                   BusinessPolicyRepository policyRepository,
                                   ProcessDocumentRepository documentRepository,
                                   RestClient mlRestClient) {
        this.instanceRepository = instanceRepository;
        this.policyRepository = policyRepository;
        this.documentRepository = documentRepository;
        this.mlRestClient = mlRestClient;
    }

    @Override
    public RouteAdvisoryResponse adviseForInstance(String instanceId, String nodeId) {
        ProcessInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Trámite no encontrado: " + instanceId));
        BusinessPolicy policy = policyRepository.findById(inst.getBusinessPolicyId())
                .orElseThrow(() -> new PolicyNotFoundException(
                        "Política no encontrada: " + inst.getBusinessPolicyId()));

        String decisionNodeId = (nodeId != null && !nodeId.isBlank())
                ? nodeId : inst.getCurrentNodeId();

        Map<String, ActivityNode> nodeById = new LinkedHashMap<>();
        if (policy.getNodes() != null) {
            policy.getNodes().forEach(n -> nodeById.put(n.getId(), n));
        }
        ActivityNode decisionNode = nodeById.get(decisionNodeId);
        String decisionLabel = decisionNode != null && decisionNode.getLabel() != null
                ? decisionNode.getLabel() : decisionNodeId;

        // Only DECISION nodes have a route to predict.
        if (decisionNode == null || decisionNode.getType() != NodeType.DECISION) {
            return notADecision(inst.getId(), decisionNodeId, decisionLabel);
        }

        // Outgoing branches = control flows whose source is this decision node.
        List<RouteAdvisoryResponse.BranchScore> offered = new ArrayList<>();
        List<Map<String, Object>> branchPayload = new ArrayList<>();
        if (policy.getFlows() != null) {
            for (ControlFlow f : policy.getFlows()) {
                if (Objects.equals(f.getSourceNodeId(), decisionNodeId)) {
                    ActivityNode target = nodeById.get(f.getTargetNodeId());
                    String label = target != null && target.getLabel() != null
                            ? target.getLabel() : f.getTargetNodeId();
                    String branchId = f.getId() != null ? f.getId() : f.getTargetNodeId();
                    offered.add(new RouteAdvisoryResponse.BranchScore(branchId, label, 0.0));
                    branchPayload.add(Map.of("branchId", branchId, "label", label));
                }
            }
        }
        if (offered.size() < 2) {
            return notADecision(inst.getId(), decisionNodeId, decisionLabel);
        }

        // ── decision-time context features ────────────────────────────────────
        Map<String, Object> ctx = inst.getContextData() != null ? inst.getContextData() : Map.of();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("processInstanceId", inst.getId());
        context.put("nodeId", decisionNodeId);
        context.put("amount", clamp(num(ctx, "amount", 0.0)));
        context.put("clientSegment", clamp(num(ctx, "clientSegment", 0.0)));
        context.put("complexity", clamp(num(ctx, "complexity", 0.0)));
        context.put("docCompleteness", clamp(docCompleteness(inst, policy)));
        context.put("zoneRural", clamp(num(ctx, "zoneRural", 0.0)));
        context.put("priorIssues", clamp(num(ctx, "priorIssues", 0.0)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", context);
        payload.put("branches", branchPayload);

        MlRouteResponse ml;
        try {
            ml = mlRestClient.post()
                    .uri("/route/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MlRouteResponse.class);
        } catch (Exception e) {
            throw new AgentUnavailableException(
                    "No se pudo predecir la ruta (servicio de IA no disponible).", e);
        }
        if (ml == null || ml.ranking() == null) {
            throw new AgentUnavailableException("Respuesta vacía del predictor de ruta.", null);
        }

        List<RouteAdvisoryResponse.BranchScore> ranking = ml.ranking().stream()
                .map(b -> new RouteAdvisoryResponse.BranchScore(
                        b.branchId(), b.label(), b.probability()))
                .toList();

        return new RouteAdvisoryResponse(
                inst.getId(), decisionNodeId, decisionLabel,
                ml.recommendedBranchId(), ml.recommendedLabel(),
                ml.confidence(), ml.confident(), ranking, ml.rationale(), ml.modelInfo());
    }

    // ── feature helpers ────────────────────────────────────────────────────────

    /** Fraction of the policy's mandatory document requirements that are CONFIRMED for this instance. */
    private double docCompleteness(ProcessInstance inst, BusinessPolicy policy) {
        List<DocumentRequirement> mandatory = policy.getDocumentRequirements() == null ? List.of()
                : policy.getDocumentRequirements().stream().filter(DocumentRequirement::isMandatory).toList();
        if (mandatory.isEmpty()) return 1.0;  // nothing required ⇒ complete

        List<ProcessDocument> docs = documentRepository.findByProcessInstanceId(inst.getId());
        long satisfied = mandatory.stream()
                .filter(req -> docs.stream().anyMatch(d ->
                        d.getStatus() == DocumentStatus.CONFIRMED
                                && Objects.equals(d.getDocumentRequirementId(), req.getId())))
                .count();
        return (double) satisfied / mandatory.size();
    }

    /** Read a numeric feature from the form data; tolerant of Number or numeric String. */
    private static double num(Map<String, Object> ctx, String key, double dflt) {
        Object v = ctx.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return dflt;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private RouteAdvisoryResponse notADecision(String instanceId, String nodeId, String label) {
        return new RouteAdvisoryResponse(
                instanceId, nodeId, label, null, null, 0.0, false, List.of(),
                "El nodo actual no es un punto de decisión con rutas alternativas.", null);
    }

    // ── ibpms_ml response mapping ──────────────────────────────────────────────

    private record MlRouteResponse(
            String recommendedBranchId, String recommendedLabel, double confidence,
            boolean confident, List<MlBranch> ranking, String rationale, String modelInfo) {}

    private record MlBranch(String branchId, String label, double probability) {}
}
