package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.BottleneckResponse;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.service.api.AnalyticsService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TODO: DEUDA TÉCNICA — El cálculo de bottlenecks se hace in-memory con findAll().
 * Para producción, reemplazar por un @Aggregation pipeline en ActivityTaskRepository
 * que agrupe por nodeId y calcule el promedio directamente en MongoDB.
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ActivityTaskRepository taskRepository;
    private final BusinessPolicyRepository policyRepository;

    public AnalyticsServiceImpl(ActivityTaskRepository taskRepository,
                                BusinessPolicyRepository policyRepository) {
        this.taskRepository = taskRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    public List<BottleneckResponse> getBottlenecks() {
        // Build nodeId → label map from all policies (one query)
        Map<String, String> labelByNodeId = policyRepository.findAll().stream()
                .filter(p -> p.getNodes() != null)
                .flatMap(p -> p.getNodes().stream())
                .collect(Collectors.toMap(
                        ActivityNode::getId,
                        ActivityNode::getLabel,
                        (a, b) -> a   // keep first on duplicate nodeId across policies
                ));

        // Filter completed tasks with both timestamps in-memory
        List<ActivityTask> completedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED
                        && t.getAssignedAt() != null
                        && t.getCompletedAt() != null)
                .toList();

        // Group by nodeId, compute average duration in seconds, sort desc
        return completedTasks.stream()
                .collect(Collectors.groupingBy(
                        ActivityTask::getNodeId,
                        Collectors.averagingDouble(t ->
                                Duration.between(t.getAssignedAt(), t.getCompletedAt()).toSeconds())
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(e -> new BottleneckResponse(
                        e.getKey(),
                        labelByNodeId.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue()
                ))
                .toList();
    }
}

