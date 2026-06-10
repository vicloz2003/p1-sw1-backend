package com.ibpms.service.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.User;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.TaskNotificationDto;
import com.ibpms.dto.response.TaskResponse;
import com.ibpms.exception.InvalidTaskStateException;
import com.ibpms.exception.TaskNotFoundException;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.api.TaskService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private record NodeInfo(Map<String, Object> formSchema, String label, String policyName) {}

    private final ActivityTaskRepository taskRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BusinessPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public TaskServiceImpl(ActivityTaskRepository taskRepository,
                           ProcessInstanceRepository processInstanceRepository,
                           BusinessPolicyRepository policyRepository,
                           UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.taskRepository = taskRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
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
        Map<String, String> clientNameByInstanceId = resolveClientNames(combined);

        return combined.stream()
                .sorted(Comparator.comparing(ActivityTask::getAssignedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toResponse(t, nodeInfoByNodeId.get(t.getNodeId()),
                        clientNameByInstanceId.get(t.getProcessInstanceId())))
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

        Map<String, NodeInfo> nodeInfoMap = resolveFormSchemas(List.of(saved));
        NodeInfo info = nodeInfoMap.get(saved.getNodeId());
        Map<String, String> clientNameMap = resolveClientNames(List.of(saved));
        String clientName = clientNameMap.get(saved.getProcessInstanceId());

        // WebSocket: notify the specific user who claimed the task (DT-10 / RF-02)
        TaskNotificationDto claimNotification = new TaskNotificationDto(
                saved.getId(),
                saved.getNodeId(),
                info != null ? info.label() : saved.getNodeId(),
                saved.getProcessInstanceId(),
                info != null ? info.policyName() : ""
        );
        messagingTemplate.convertAndSend("/queue/user/" + userId, claimNotification);

        return toResponse(saved, info, clientName);
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

        // nodeId → NodeInfo (formSchema + label + policyName)
        Map<String, NodeInfo> result = new HashMap<>();
        for (BusinessPolicy policy : policiesById.values()) {
            if (policy.getNodes() == null) continue;
            for (ActivityNode node : policy.getNodes()) {
                result.putIfAbsent(node.getId(),
                        new NodeInfo(node.getFormSchema(), node.getLabel(), policy.getName()));
            }
        }
        return result;
    }

    /**
     * Resolves clientName for each task via 2 DB queries:
     * tasks → process instances (clientId) → users (username).
     * Returns a map of processInstanceId → clientName.
     */
    private Map<String, String> resolveClientNames(List<ActivityTask> tasks) {
        if (tasks.isEmpty()) return Map.of();

        Set<String> instanceIds = tasks.stream()
                .map(ActivityTask::getProcessInstanceId)
                .collect(Collectors.toSet());
        Map<String, ProcessInstance> instancesById = processInstanceRepository
                .findAllById(instanceIds).stream()
                .collect(Collectors.toMap(ProcessInstance::getId, Function.identity()));

        Set<String> clientIds = instancesById.values().stream()
                .map(ProcessInstance::getClientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> usernameById = userRepository
                .findAllById(clientIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        return instancesById.entrySet().stream()
                .filter(e -> e.getValue().getClientId() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> usernameById.getOrDefault(e.getValue().getClientId(), null)
                ));
    }

    private TaskResponse toResponse(ActivityTask task, NodeInfo info, String clientName) {
        return new TaskResponse(
                task.getId(),
                task.getNodeId(),
                info != null ? info.label() : task.getNodeId(),
                task.getProcessInstanceId(),
                task.getAssignedDepartmentId(),
                task.getStatus(),
                info != null ? info.formSchema() : null,
                task.getAssignedAt(),
                task.getStartedAt(),
                info != null ? info.policyName() : null,
                clientName
        );
    }
}

