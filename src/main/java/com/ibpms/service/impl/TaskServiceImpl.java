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

        List<ActivityTask> combined = new ArrayList<>(pending.size() + inProgress.size());
        combined.addAll(pending);
        combined.addAll(inProgress);

        // Batch-load formSchema: 2 queries regardless of task count
        Map<String, Map<String, Object>> formSchemaByNodeId = resolveFormSchemas(combined);

        return combined.stream()
                .sorted(Comparator.comparing(ActivityTask::getAssignedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toResponse(t, formSchemaByNodeId.get(t.getNodeId())))
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

        Map<String, Map<String, Object>> schemas = resolveFormSchemas(List.of(saved));
        return toResponse(saved, schemas.get(saved.getNodeId()));
    }

    /**
     * Resolves formSchema for each task via 2 DB queries:
     * tasks → process instances → business policies → node formSchema.
     */
    private Map<String, Map<String, Object>> resolveFormSchemas(List<ActivityTask> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        Set<String> instanceIds = tasks.stream()
                .map(ActivityTask::getProcessInstanceId)
                .collect(Collectors.toSet());
        Map<String, ProcessInstance> instancesById = processInstanceRepository
                .findAllById(instanceIds)
                .stream()
                .collect(Collectors.toMap(ProcessInstance::getId, Function.identity()));

        Set<String> policyIds = instancesById.values().stream()
                .map(ProcessInstance::getBusinessPolicyId)
                .collect(Collectors.toSet());
        Map<String, BusinessPolicy> policiesById = policyRepository
                .findAllById(policyIds)
                .stream()
                .collect(Collectors.toMap(BusinessPolicy::getId, Function.identity()));

        // nodeId → formSchema (across all relevant policies)
        Map<String, Map<String, Object>> schemaByNodeId = policiesById.values().stream()
                .flatMap(p -> p.getNodes() != null ? p.getNodes().stream() : java.util.stream.Stream.empty())
                .filter(n -> n.getFormSchema() != null)
                .collect(Collectors.toMap(
                        ActivityNode::getId,
                        ActivityNode::getFormSchema,
                        (a, b) -> a   // keep first on duplicate nodeId (edge case)
                ));
        return schemaByNodeId;
    }

    private TaskResponse toResponse(ActivityTask task, Map<String, Object> formSchema) {
        return new TaskResponse(
                task.getId(),
                task.getNodeId(),
                task.getProcessInstanceId(),
                task.getAssignedDepartmentId(),
                task.getStatus(),
                formSchema,
                task.getAssignedAt()
        );
    }
}

