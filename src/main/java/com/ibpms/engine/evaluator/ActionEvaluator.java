package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.TaskNotificationDto;
import com.ibpms.engine.router.FlowRouter;
import com.ibpms.repository.ActivityTaskRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

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

    public ActionEvaluator(ActivityTaskRepository taskRepository,
                           SimpMessagingTemplate messagingTemplate,
                           FlowRouter flowRouter) {
        this.taskRepository = taskRepository;
        this.messagingTemplate = messagingTemplate;
        this.flowRouter = flowRouter;
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
            TaskNotificationDto notification = new TaskNotificationDto(
                    saved.getId(),
                    node.getId(),
                    node.getLabel(),
                    instance.getId(),
                    policy.getName()
            );
            messagingTemplate.convertAndSend("/topic/department/" + departmentId, notification);
        }

        return List.of(); // Blocks until completeTask() is called
    }
}

