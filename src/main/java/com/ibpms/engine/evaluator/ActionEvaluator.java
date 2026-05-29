package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.User;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.TaskNotificationDto;
import com.ibpms.engine.router.FlowRouter;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.impl.PushNotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ACTION: creates an ActivityTask for the node's department partition, emits a
 * WebSocket notification, then returns an empty list to block the flow until
 * WorkflowEngine.completeTask() is called.
 */
@Component
public class ActionEvaluator implements NodeEvaluator {

    private final ActivityTaskRepository taskRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FlowRouter flowRouter;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;

    public ActionEvaluator(ActivityTaskRepository taskRepository,
                           SimpMessagingTemplate messagingTemplate,
                           FlowRouter flowRouter,
                           UserRepository userRepository,
                           PushNotificationService pushNotificationService) {
        this.taskRepository = taskRepository;
        this.messagingTemplate = messagingTemplate;
        this.flowRouter = flowRouter;
        this.userRepository = userRepository;
        this.pushNotificationService = pushNotificationService;
    }

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.ACTION;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        String departmentId = flowRouter.findPartitionById(node.getPartitionId(), policy)
                .map(ActivityPartition::getDepartmentId)
                .orElse(null);

        ActivityTask task = new ActivityTask();
        task.setProcessInstanceId(instance.getId());
        task.setNodeId(node.getId());
        task.setAssignedDepartmentId(departmentId);
        task.setStatus(TaskStatus.PENDING);
        task.setAssignedAt(LocalDateTime.now());
        task.setFormData(new HashMap<>());
        ActivityTask saved = taskRepository.save(task);

        if (departmentId != null) {
            // WebSocket notification (DT-10) — existing behaviour
            TaskNotificationDto notification = new TaskNotificationDto(
                    saved.getId(),
                    node.getId(),
                    node.getLabel(),
                    instance.getId(),
                    policy.getName()
            );
            messagingTemplate.convertAndSend("/topic/department/" + departmentId, notification);

            // FCM push notification to all employees of the department who have
            // a registered mobile device (RF-29)
            List<String> fcmTokens = userRepository.findByDepartmentId(departmentId)
                    .stream()
                    .map(User::getFcmToken)
                    .filter(token -> token != null && !token.isBlank())
                    .toList();

            if (!fcmTokens.isEmpty()) {
                pushNotificationService.sendToMultipleTokens(
                        fcmTokens,
                        "Nueva tarea asignada",
                        "Tienes una tarea pendiente: " + node.getLabel()
                                + " en la politica '" + policy.getName() + "'.",
                        Map.of("taskId", saved.getId(),
                               "policyName", policy.getName(),
                               "nodeLabel", node.getLabel() != null ? node.getLabel() : "")
                );
            }
        }

        return List.of(); // Blocks until completeTask() is called
    }
}

