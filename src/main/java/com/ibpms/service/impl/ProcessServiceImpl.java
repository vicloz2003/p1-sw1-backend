package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.Department;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.request.StartProcessRequest;
import com.ibpms.dto.response.NodeProgressItem;
import com.ibpms.dto.response.ProcessStatusResponse;
import com.ibpms.engine.api.WorkflowEngine;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.ProcessService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProcessServiceImpl implements ProcessService {

    private final WorkflowEngine workflowEngine;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BusinessPolicyRepository policyRepository;
    private final ActivityTaskRepository taskRepository;
    private final DepartmentRepository departmentRepository;

    public ProcessServiceImpl(WorkflowEngine workflowEngine,
                              ProcessInstanceRepository processInstanceRepository,
                              BusinessPolicyRepository policyRepository,
                              ActivityTaskRepository taskRepository,
                              DepartmentRepository departmentRepository) {
        this.workflowEngine = workflowEngine;
        this.processInstanceRepository = processInstanceRepository;
        this.policyRepository = policyRepository;
        this.taskRepository = taskRepository;
        this.departmentRepository = departmentRepository;
    }

    @Override
    public ProcessStatusResponse startProcess(StartProcessRequest request, String userId) {
        ProcessInstance instance = workflowEngine.startProcess(
                request.policyId(),
                userId,
                request.initialData() != null ? request.initialData() : Collections.emptyMap()
        );
        instance.setClientId(request.clientId());
        processInstanceRepository.save(instance);
        return toStatusResponse(instance);
    }

    @Override
    public ProcessStatusResponse getStatus(String processInstanceId) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Process instance not found: " + processInstanceId));
        return toStatusResponse(instance);
    }

    @Override
    public List<ProcessStatusResponse> getByClientId(String clientId) {
        return processInstanceRepository
                .findByClientId(clientId)
                .stream()
                .map(this::toStatusResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ProcessStatusResponse toStatusResponse(ProcessInstance instance) {
        String policyName = "";
        String currentNodeLabel = instance.getCurrentNodeId();
        String currentDepartmentId = null;
        String currentDepartmentName = null;
        List<NodeProgressItem> nodeProgress = List.of();

        var policyOpt = policyRepository.findById(instance.getBusinessPolicyId());
        if (policyOpt.isPresent()) {
            BusinessPolicy policy = policyOpt.get();
            policyName = policy.getName();

            // Resolve current node label
            ActivityNode currentNode = policy.getNodes() == null ? null :
                    policy.getNodes().stream()
                            .filter(n -> n.getId().equals(instance.getCurrentNodeId()))
                            .findFirst()
                            .orElse(null);

            if (currentNode != null) {
                currentNodeLabel = currentNode.getLabel();

                // Resolve current department from partition
                if (currentNode.getPartitionId() != null && policy.getPartitions() != null) {
                    currentDepartmentId = policy.getPartitions().stream()
                            .filter(p -> p.getId().equals(currentNode.getPartitionId()))
                            .map(ActivityPartition::getDepartmentId)
                            .findFirst()
                            .orElse(null);

                    if (currentDepartmentId != null) {
                        currentDepartmentName = departmentRepository.findById(currentDepartmentId)
                                .map(Department::name)
                                .orElse(currentDepartmentId);
                    }
                }
            }

            // Build visual progress timeline (RF-00b)
            nodeProgress = buildNodeProgress(instance, policy);
        }

        return new ProcessStatusResponse(
                instance.getId(),
                instance.getCurrentNodeId(),
                currentNodeLabel,
                currentDepartmentId,
                currentDepartmentName,
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                instance.getClientId(),
                policyName,
                nodeProgress
        );
    }

    /**
     * Builds the visual timeline for the Flutter app (RF-00b).
     * Only ACTION nodes are shown (they are the meaningful user-facing steps).
     * Status: COMPLETED if the task exists and is done; CURRENT if in progress/pending;
     * PENDING if the process hasn't reached that node yet.
     */
    private List<NodeProgressItem> buildNodeProgress(ProcessInstance instance, BusinessPolicy policy) {
        if (policy.getNodes() == null) return List.of();

        // Fetch all tasks for this instance
        List<ActivityTask> tasks = taskRepository.findByProcessInstanceId(instance.getId());
        Map<String, ActivityTask> taskByNodeId = tasks.stream()
                .collect(Collectors.toMap(ActivityTask::getNodeId, Function.identity(), (a, b) -> a));

        // Build department name lookup
        Set<String> deptIds = policy.getPartitions() == null ? Set.of() :
                policy.getPartitions().stream()
                        .map(ActivityPartition::getDepartmentId)
                        .collect(Collectors.toSet());
        Map<String, String> deptNameById = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::id, Department::name));

        Map<String, String> deptIdByPartitionId = policy.getPartitions() == null ? Map.of() :
                policy.getPartitions().stream()
                        .collect(Collectors.toMap(ActivityPartition::getId,
                                ActivityPartition::getDepartmentId, (a, b) -> a));

        return policy.getNodes().stream()
                .filter(n -> n.getType() == NodeType.ACTION) // Only show ACTION nodes in timeline
                .map(node -> {
                    String deptId = deptIdByPartitionId.get(node.getPartitionId());
                    String deptName = deptId != null ? deptNameById.getOrDefault(deptId, deptId) : null;

                    ActivityTask task = taskByNodeId.get(node.getId());
                    String progressStatus;
                    java.time.LocalDateTime completedAt = null;

                    if (task == null) {
                        progressStatus = "PENDING";
                    } else if (task.getStatus() == TaskStatus.COMPLETED) {
                        progressStatus = "COMPLETED";
                        completedAt = task.getCompletedAt();
                    } else {
                        progressStatus = "CURRENT";
                    }

                    return new NodeProgressItem(
                            node.getId(),
                            node.getLabel(),
                            deptName,
                            progressStatus,
                            completedAt
                    );
                })
                .toList();
    }
}
