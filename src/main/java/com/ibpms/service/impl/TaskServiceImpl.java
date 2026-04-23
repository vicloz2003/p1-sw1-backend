package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.TaskResponse;
import com.ibpms.exception.InvalidTaskStateException;
import com.ibpms.exception.TaskNotFoundException;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.TaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private record NodeInfo(Map<String, Object> formSchema, String label) {}
    private final ActivityTaskRepository taskRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BusinessPolicyRepository policyRepository;

    public TaskServiceImpl(ActivityTaskRepository taskRepository,
                           ProcessInstanceRepository processInstanceRepository,
                           BusinessPolicyRepository policyRepository) {
        this.taskRepository = taskRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    public List<TaskResponse> getMyTasks(String departmentId, String userId) {
        List<ActivityTask> pending = taskRepository
                .findByAssignedDepartmentIdAndStatus(departmentId, TaskStatus.PENDING);
        List<ActivityTask> inProgress = taskRepository
                .findByAssignedUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        List<ActivityTask> completed = taskRepository          // ← AGREGAR
                .findByAssignedDepartmentIdAndStatus(departmentId, TaskStatus.COMPLETED);

        List<ActivityTask> combined = new ArrayList<>(pending.size() + inProgress.size() + completed.size());
        combined.addAll(pending);
        combined.addAll(inProgress);
        combined.addAll(completed);


        Map<String, NodeInfo> nodeInfoByNodeId = resolveFormSchemas(combined);

        return combined.stream()
                .sorted(Comparator.comparing(ActivityTask::getAssignedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toResponse(t, nodeInfoByNodeId.get(t.getNodeId())))
                .toList();
    }

    @Override
    public TaskResponse claim(String taskId, String userId) {
        ActivityTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidTaskStateException(
                    "Task cannot be claimed. Current status: " + task.getStatus());
        }
        task.setAssignedUserId(userId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(LocalDateTime.now());
        ActivityTask saved = taskRepository.save(task);

        Map<String, NodeInfo> nodeInfo = resolveFormSchemas(List.of(saved));
        return toResponse(saved, nodeInfo.get(saved.getNodeId()));
    }

    /**
     * Resolves formSchema for each task via 2 DB queries:
     * tasks → process instances → business policies → node formSchema.
     */

    private Map<String, NodeInfo> resolveFormSchemas(List<ActivityTask> tasks) {
        if (tasks.isEmpty()) return Map.of();

        Set<String> instanceIds = tasks.stream()
                .map(ActivityTask::getProcessInstanceId)
                .collect(Collectors.toSet());
        Map<String, ProcessInstance> instancesById = processInstanceRepository
                .findAllById(instanceIds).stream()
                .collect(Collectors.toMap(ProcessInstance::getId, Function.identity()));

        Set<String> policyIds = instancesById.values().stream()
                .map(ProcessInstance::getBusinessPolicyId)
                .collect(Collectors.toSet());
        Map<String, BusinessPolicy> policiesById = policyRepository
                .findAllById(policyIds).stream()
                .collect(Collectors.toMap(BusinessPolicy::getId, Function.identity()));

        return policiesById.values().stream()
                .flatMap(p -> p.getNodes() != null
                        ? p.getNodes().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.toMap(
                        ActivityNode::getId,
                        n -> new NodeInfo(n.getFormSchema(), n.getLabel()),
                        (a, b) -> a
                ));
    }


    private TaskResponse toResponse(ActivityTask task, NodeInfo info) {
        return new TaskResponse(
                task.getId(),
                task.getNodeId(),
                info != null ? info.label() : task.getNodeId(),
                task.getProcessInstanceId(),
                task.getAssignedDepartmentId(),
                task.getStatus(),
                info != null ? info.formSchema() : null,
                task.getAssignedAt()
        );
    }
}

