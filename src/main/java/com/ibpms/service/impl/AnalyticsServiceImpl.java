package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.*;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.api.AnalyticsService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * NOTE: All calculations are currently in-memory (findAll + Java stream).
 * For production, replace with MongoDB $group aggregation pipelines.
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ActivityTaskRepository taskRepository;
    private final BusinessPolicyRepository policyRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final UserRepository userRepository;

    public AnalyticsServiceImpl(ActivityTaskRepository taskRepository,
                                BusinessPolicyRepository policyRepository,
                                ProcessInstanceRepository instanceRepository,
                                UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.policyRepository = policyRepository;
        this.instanceRepository = instanceRepository;
        this.userRepository = userRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-A1: Bottlenecks
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<BottleneckResponse> getBottlenecks() {
        Map<String, String> labelByNodeId = buildNodeLabelMap(policyRepository.findAll());
        return computeBottlenecks(taskRepository.findByStatus(TaskStatus.COMPLETED), labelByNodeId);
    }

    @Override
    public List<BottleneckResponse> getBottlenecksByPolicy(String policyId) {
        BusinessPolicy policy = policyRepository.findById(policyId).orElse(null);
        if (policy == null) return List.of();

        Map<String, String> labelByNodeId = buildNodeLabelMap(List.of(policy));

        // Only tasks whose processInstance belongs to this policy
        Set<String> instanceIds = instanceRepository.findByBusinessPolicyId(policyId)
                .stream().map(ProcessInstance::getId).collect(Collectors.toSet());

        List<ActivityTask> completedTasks = taskRepository.findByStatus(TaskStatus.COMPLETED)
                .stream()
                .filter(t -> instanceIds.contains(t.getProcessInstanceId()))
                .filter(t -> t.getAssignedAt() != null && t.getCompletedAt() != null)
                .toList();

        return computeBottlenecks(completedTasks, labelByNodeId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-A2: Employee performance
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<EmployeePerformanceResponse> getEmployeePerformance() {
        return computeEmployeePerformance(taskRepository.findByStatus(TaskStatus.COMPLETED));
    }

    /**
     * Computes per-employee performance over a given set of completed tasks.
     *
     * <p>Work time is measured as {@code startedAt → completedAt} (actual time the
     * employee spent on the task), NOT {@code assignedAt → completedAt}, so that an
     * employee is not penalized for the period a task sat unclaimed in the queue.
     */
    private List<EmployeePerformanceResponse> computeEmployeePerformance(List<ActivityTask> source) {
        List<ActivityTask> completed = source.stream()
                .filter(t -> t.getAssignedUserId() != null
                        && t.getStartedAt() != null
                        && t.getCompletedAt() != null)
                .toList();

        if (completed.isEmpty()) return List.of();

        // Global average per nodeId (work time)
        Map<String, Double> globalAvgByNode = completed.stream()
                .collect(Collectors.groupingBy(
                        ActivityTask::getNodeId,
                        Collectors.averagingDouble(t ->
                                Duration.between(t.getStartedAt(), t.getCompletedAt()).toSeconds())
                ));

        // Username lookup map
        Map<String, String> usernameById = userRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));
        Map<String, String> departmentById = userRepository.findAll().stream()
                .filter(u -> u.getDepartmentId() != null)
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getDepartmentId(), (a, b) -> a));

        // Group tasks by user
        Map<String, List<ActivityTask>> tasksByUser = completed.stream()
                .collect(Collectors.groupingBy(ActivityTask::getAssignedUserId));

        return tasksByUser.entrySet().stream()
                .map(entry -> {
                    String userId = entry.getKey();
                    List<ActivityTask> userTasks = entry.getValue();

                    double userAvg = userTasks.stream()
                            .mapToLong(t -> Duration.between(t.getStartedAt(), t.getCompletedAt()).toSeconds())
                            .average()
                            .orElse(0);

                    // Global avg for the same nodes this user worked on
                    double globalAvgSameNodes = userTasks.stream()
                            .mapToDouble(t -> globalAvgByNode.getOrDefault(t.getNodeId(), userAvg))
                            .average()
                            .orElse(userAvg);

                    double ratio = globalAvgSameNodes > 0 ? userAvg / globalAvgSameNodes : 1.0;

                    String level;
                    if (ratio <= 0.85) level = "GOOD";
                    else if (ratio <= 1.15) level = "AVERAGE";
                    else level = "POOR";

                    return new EmployeePerformanceResponse(
                            userId,
                            usernameById.getOrDefault(userId, userId),
                            departmentById.getOrDefault(userId, null),
                            Math.round(userAvg * 100.0) / 100.0,
                            Math.round(globalAvgSameNodes * 100.0) / 100.0,
                            Math.round(ratio * 1000.0) / 1000.0,
                            userTasks.size(),
                            level
                    );
                })
                .sorted(Comparator.comparingDouble(EmployeePerformanceResponse::performanceRatio).reversed())
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-A3: Throughput
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ThroughputResponse getThroughput(String policyId, String period) {
        BusinessPolicy policy = policyRepository.findById(policyId).orElse(null);
        String policyName = policy != null ? policy.getName() : policyId;

        LocalDateTime since = switch (period.toUpperCase()) {
            case "DAILY"   -> LocalDateTime.now().minusDays(1);
            case "WEEKLY"  -> LocalDateTime.now().minusWeeks(1);
            case "MONTHLY" -> LocalDateTime.now().minusMonths(1);
            default        -> LocalDateTime.now().minusMonths(1);
        };

        List<ProcessInstance> instances = instanceRepository.findByBusinessPolicyId(policyId)
                .stream()
                .filter(i -> i.getStartedAt() != null && i.getStartedAt().isAfter(since))
                .toList();

        long initiated  = instances.size();
        long completed  = instances.stream().filter(i -> i.getStatus() == InstanceStatus.COMPLETED).count();
        long cancelled  = instances.stream().filter(i -> i.getStatus() == InstanceStatus.CANCELLED).count();
        double rate     = initiated > 0 ? Math.round((completed * 100.0 / initiated) * 100.0) / 100.0 : 0;

        return new ThroughputResponse(period.toUpperCase(), policyId, policyName, initiated, completed, cancelled, rate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-A4: SLA compliance
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<SlaComplianceResponse> getSlaCompliance(String policyId) {
        BusinessPolicy policy = policyRepository.findById(policyId).orElse(null);
        if (policy == null || policy.getNodes() == null) return List.of();

        // Only ACTION nodes with slaSeconds defined
        List<ActivityNode> slaNodes = policy.getNodes().stream()
                .filter(n -> n.getMetadata() != null && n.getMetadata().containsKey("slaSeconds"))
                .toList();

        if (slaNodes.isEmpty()) return List.of();

        Set<String> instanceIds = instanceRepository.findByBusinessPolicyId(policyId)
                .stream().map(ProcessInstance::getId).collect(Collectors.toSet());

        List<ActivityTask> allCompleted = taskRepository.findByStatus(TaskStatus.COMPLETED)
                .stream()
                .filter(t -> instanceIds.contains(t.getProcessInstanceId())
                        && t.getAssignedAt() != null && t.getCompletedAt() != null)
                .toList();

        Map<String, List<ActivityTask>> tasksByNode = allCompleted.stream()
                .collect(Collectors.groupingBy(ActivityTask::getNodeId));

        return slaNodes.stream().map(node -> {
            long slaSeconds;
            try {
                slaSeconds = Long.parseLong(node.getMetadata().get("slaSeconds"));
            } catch (NumberFormatException e) {
                return null;
            }
            List<ActivityTask> nodeTasks = tasksByNode.getOrDefault(node.getId(), List.of());
            long withinSla = nodeTasks.stream()
                    .filter(t -> Duration.between(t.getAssignedAt(), t.getCompletedAt()).toSeconds() <= slaSeconds)
                    .count();
            double compliance = nodeTasks.isEmpty() ? 0.0
                    : Math.round((withinSla * 100.0 / nodeTasks.size()) * 100.0) / 100.0;

            return new SlaComplianceResponse(
                    node.getId(), node.getLabel(),
                    policyId, policy.getName(),
                    slaSeconds, nodeTasks.size(), withinSla, compliance
            );
        }).filter(Objects::nonNull).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-A5: Abandonment rate
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public AbandonmentResponse getAbandonmentRate(String policyId) {
        BusinessPolicy policy = policyRepository.findById(policyId).orElse(null);
        String policyName = policy != null ? policy.getName() : policyId;

        List<ProcessInstance> instances = instanceRepository.findByBusinessPolicyId(policyId);
        long total     = instances.size();
        long completed = instances.stream().filter(i -> i.getStatus() == InstanceStatus.COMPLETED).count();
        long cancelled = instances.stream().filter(i -> i.getStatus() == InstanceStatus.CANCELLED).count();
        double rate    = total > 0 ? Math.round((cancelled * 100.0 / total) * 100.0) / 100.0 : 0;

        return new AbandonmentResponse(policyId, policyName, total, completed, cancelled, rate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF-A6: Consolidated dashboard
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public AnalyticsDashboardResponse getDashboard(String policyId) {
        BusinessPolicy policy = policyRepository.findById(policyId).orElse(null);
        String policyName = policy != null ? policy.getName() : policyId;

        List<ProcessInstance> instances = instanceRepository.findByBusinessPolicyId(policyId);
        long active    = instances.stream().filter(i -> i.getStatus() == InstanceStatus.ACTIVE).count();
        long completed = instances.stream().filter(i -> i.getStatus() == InstanceStatus.COMPLETED).count();
        long cancelled = instances.stream().filter(i -> i.getStatus() == InstanceStatus.CANCELLED).count();
        long total     = instances.size();
        double abandonment = total > 0 ? Math.round((cancelled * 100.0 / total) * 100.0) / 100.0 : 0;

        // Average completion time in hours for completed instances
        OptionalDouble avgMinutes = instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.COMPLETED
                        && i.getStartedAt() != null && i.getCompletedAt() != null)
                .mapToLong(i -> Duration.between(i.getStartedAt(), i.getCompletedAt()).toMinutes())
                .average();
        double avgHours = avgMinutes.isPresent()
                ? Math.round((avgMinutes.getAsDouble() / 60.0) * 100.0) / 100.0
                : 0;

        List<BottleneckResponse> topBottlenecks = getBottlenecksByPolicy(policyId).stream()
                .limit(5)
                .toList();

        // Scope poor performers to THIS policy only (consistent with the rest of the
        // dashboard). Previously this used the global ranking across all policies.
        Set<String> policyInstanceIds = instances.stream()
                .map(ProcessInstance::getId)
                .collect(Collectors.toSet());
        List<ActivityTask> policyCompletedTasks = taskRepository.findByStatus(TaskStatus.COMPLETED)
                .stream()
                .filter(t -> policyInstanceIds.contains(t.getProcessInstanceId()))
                .toList();

        List<EmployeePerformanceResponse> poorPerformers = computeEmployeePerformance(policyCompletedTasks).stream()
                .filter(e -> "POOR".equals(e.performanceLevel()))
                .limit(5)
                .toList();

        return new AnalyticsDashboardResponse(
                policyId, policyName,
                active, completed, cancelled,
                abandonment, avgHours,
                topBottlenecks, poorPerformers
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, String> buildNodeLabelMap(List<BusinessPolicy> policies) {
        return policies.stream()
                .filter(p -> p.getNodes() != null)
                .flatMap(p -> p.getNodes().stream())
                .collect(Collectors.toMap(ActivityNode::getId, ActivityNode::getLabel, (a, b) -> a));
    }

    private List<BottleneckResponse> computeBottlenecks(List<ActivityTask> tasks,
                                                         Map<String, String> labelByNodeId) {
        return tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED
                        && t.getAssignedAt() != null && t.getCompletedAt() != null)
                .collect(Collectors.groupingBy(
                        ActivityTask::getNodeId,
                        Collectors.averagingDouble(t ->
                                Duration.between(t.getAssignedAt(), t.getCompletedAt()).toMinutes() / 60.0)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(e -> new BottleneckResponse(
                        e.getKey(),
                        labelByNodeId.getOrDefault(e.getKey(), e.getKey()),
                        Math.round(e.getValue() * 100.0) / 100.0
                ))
                .toList();
    }
}
