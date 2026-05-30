package com.ibpms.service.impl;

import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.PolicyRiskResponse;
import com.ibpms.dto.response.RiskInstanceResponse;
import com.ibpms.exception.AgentUnavailableException;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.RiskService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds health features for every ACTIVE instance of a policy and delegates the scoring to
 * the ibpms_ml autoencoder (RF-3). All features are ratios/counts so the ML model stays
 * policy-agnostic (no hard-coded business semantics, per the project conventions).
 */
@Service
public class RiskServiceImpl implements RiskService {

    private static final double DEFAULT_EXPECTED_HOURS = 24.0;  // fallback when no history yet

    private final ProcessInstanceRepository instanceRepository;
    private final ActivityTaskRepository taskRepository;
    private final BusinessPolicyRepository policyRepository;
    private final RestClient mlRestClient;

    public RiskServiceImpl(ProcessInstanceRepository instanceRepository,
                           ActivityTaskRepository taskRepository,
                           BusinessPolicyRepository policyRepository,
                           RestClient mlRestClient) {
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.policyRepository = policyRepository;
        this.mlRestClient = mlRestClient;
    }

    @Override
    public PolicyRiskResponse assessPolicy(String policyId) {
        BusinessPolicy policy = policyRepository.findById(policyId).orElse(null);
        String policyName = policy != null ? policy.getName() : policyId;

        List<ProcessInstance> active = instanceRepository
                .findByBusinessPolicyIdAndStatus(policyId, InstanceStatus.ACTIVE);

        if (active.isEmpty()) {
            return new PolicyRiskResponse(policyId, policyName, 0, 0, 0.0,
                    "Sin trámites activos para evaluar.", List.of());
        }

        // ── policy-level baselines (from history) ────────────────────────────
        List<ProcessInstance> all = instanceRepository.findByBusinessPolicyId(policyId);
        double expectedHours = averageCompletionHours(all);

        // Global average task work-time (startedAt→completedAt) per node, for this policy.
        Set<String> policyInstanceIds = all.stream().map(ProcessInstance::getId).collect(Collectors.toSet());
        List<ActivityTask> completedPolicyTasks = taskRepository.findByStatus(TaskStatus.COMPLETED).stream()
                .filter(t -> policyInstanceIds.contains(t.getProcessInstanceId())
                        && t.getStartedAt() != null && t.getCompletedAt() != null)
                .toList();
        Map<String, Double> avgWorkByNode = completedPolicyTasks.stream()
                .collect(Collectors.groupingBy(ActivityTask::getNodeId,
                        Collectors.averagingDouble(t -> workSeconds(t))));
        double globalAvgWork = completedPolicyTasks.stream()
                .mapToDouble(RiskServiceImpl::workSeconds).average().orElse(0.0);
        double maxNodeAvg = avgWorkByNode.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        LocalDateTime now = LocalDateTime.now();

        // ── per-instance feature vectors ─────────────────────────────────────
        List<Map<String, Object>> featurePayload = new ArrayList<>();
        Map<String, ProcessInstance> instanceById = new HashMap<>();
        Map<String, Double> elapsedHoursById = new HashMap<>();

        for (ProcessInstance inst : active) {
            instanceById.put(inst.getId(), inst);
            LocalDateTime startedAt = inst.getStartedAt() != null ? inst.getStartedAt() : now;
            double elapsedHours = Duration.between(startedAt, now).toMinutes() / 60.0;
            elapsedHoursById.put(inst.getId(), elapsedHours);

            List<ActivityTask> tasks = taskRepository.findByProcessInstanceId(inst.getId());
            int total = tasks.size();
            long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
            long unclaimed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
            long distinctNodes = tasks.stream().map(ActivityTask::getNodeId).distinct().count();

            double elapsedRatio = expectedHours > 0 ? elapsedHours / expectedHours : 0.0;
            double progressRatio = total > 0 ? (double) completed / total : 0.0;

            // avgTaskRatio: this instance's completed-task pace vs the policy's per-node norm.
            List<ActivityTask> instCompleted = tasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.COMPLETED
                            && t.getStartedAt() != null && t.getCompletedAt() != null)
                    .toList();
            double avgTaskRatio = 1.0;
            if (!instCompleted.isEmpty()) {
                double instAvg = instCompleted.stream().mapToDouble(RiskServiceImpl::workSeconds).average().orElse(0.0);
                double normAvg = instCompleted.stream()
                        .mapToDouble(t -> avgWorkByNode.getOrDefault(t.getNodeId(), instAvg)).average().orElse(instAvg);
                avgTaskRatio = normAvg > 0 ? instAvg / normAvg : 1.0;
            }

            // maxQueueRatio: longest a task waited (assigned→started, or assigned→now if waiting).
            double maxQueueSeconds = tasks.stream().mapToDouble(t -> queueSeconds(t, now)).max().orElse(0.0);
            double maxQueueRatio = globalAvgWork > 0 ? maxQueueSeconds / globalAvgWork : 0.0;

            double reassignmentRatio = total > 0 ? (double) unclaimed / total : 0.0;

            double currentNodeAvg = inst.getCurrentNodeId() != null
                    ? avgWorkByNode.getOrDefault(inst.getCurrentNodeId(), 0.0) : 0.0;
            double bottleneckPressure = maxNodeAvg > 0 ? currentNodeAvg / maxNodeAvg : 0.0;

            double reworkRatio = total > 0 ? (double) (total - distinctNodes) / total : 0.0;

            Map<String, Object> f = new LinkedHashMap<>();
            f.put("processInstanceId", inst.getId());
            f.put("policyId", policyId);
            f.put("elapsedRatio", round(elapsedRatio));
            f.put("progressRatio", round(progressRatio));
            f.put("avgTaskRatio", round(avgTaskRatio));
            f.put("maxQueueRatio", round(maxQueueRatio));
            f.put("reassignmentRatio", round(reassignmentRatio));
            f.put("bottleneckPressure", round(bottleneckPressure));
            f.put("reworkRatio", round(reworkRatio));
            featurePayload.add(f);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instances", featurePayload);

        MlRiskResponse ml;
        try {
            ml = mlRestClient.post()
                    .uri("/risk/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MlRiskResponse.class);
        } catch (Exception e) {
            throw new AgentUnavailableException(
                    "No se pudo evaluar el riesgo (servicio de IA no disponible).", e);
        }
        if (ml == null || ml.assessments() == null) {
            throw new AgentUnavailableException("Respuesta vacía del servicio de riesgos.", null);
        }

        List<RiskInstanceResponse> instances = ml.assessments().stream()
                .map(a -> {
                    ProcessInstance inst = instanceById.get(a.processInstanceId());
                    return new RiskInstanceResponse(
                            a.processInstanceId(),
                            inst != null ? inst.getClientId() : null,
                            inst != null ? inst.getCurrentNodeId() : null,
                            round(elapsedHoursById.getOrDefault(a.processInstanceId(), 0.0)),
                            a.riskScore(), a.anomaly(), a.priority(),
                            a.drivers() != null ? a.drivers() : List.of(),
                            a.recommendation());
                })
                .sorted(Comparator.comparingDouble(RiskInstanceResponse::riskScore).reversed())
                .toList();

        long anomalies = instances.stream().filter(RiskInstanceResponse::anomaly).count();

        return new PolicyRiskResponse(policyId, policyName, instances.size(), anomalies,
                ml.threshold(), ml.modelInfo(), instances);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static double workSeconds(ActivityTask t) {
        return Duration.between(t.getStartedAt(), t.getCompletedAt()).toSeconds();
    }

    /** Time a task waited before being worked: assigned→started, or assigned→now if still waiting. */
    private static double queueSeconds(ActivityTask t, LocalDateTime now) {
        if (t.getAssignedAt() == null) return 0.0;
        LocalDateTime end = t.getStartedAt() != null ? t.getStartedAt()
                : (t.getStatus() == TaskStatus.PENDING ? now : t.getAssignedAt());
        return Math.max(0.0, Duration.between(t.getAssignedAt(), end).toSeconds());
    }

    private static double averageCompletionHours(List<ProcessInstance> instances) {
        OptionalDouble avg = instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.COMPLETED
                        && i.getStartedAt() != null && i.getCompletedAt() != null)
                .mapToDouble(i -> Duration.between(i.getStartedAt(), i.getCompletedAt()).toMinutes() / 60.0)
                .average();
        return avg.isPresent() && avg.getAsDouble() > 0 ? avg.getAsDouble() : DEFAULT_EXPECTED_HOURS;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    // ── ibpms_ml response mapping ──────────────────────────────────────────────

    private record MlRiskResponse(List<MlAssessment> assessments, double threshold, String modelInfo) {}

    private record MlAssessment(
            String processInstanceId, double riskScore, boolean anomaly, String priority,
            double reconstructionError, List<String> drivers, String recommendation) {}
}
