package com.ibpms.engine.impl;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityTask;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.PolicyStatus;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.dto.response.ProcessStateNotificationDto;
import com.ibpms.engine.api.WorkflowEngine;
import com.ibpms.engine.evaluator.NodeEvaluator;
import com.ibpms.engine.router.FlowRouter;
import com.ibpms.exception.InvalidTaskStateException;
import com.ibpms.exception.PolicyNotFoundException;
import com.ibpms.exception.PolicyNotActiveException;
import com.ibpms.exception.TaskNotFoundException;
import com.ibpms.repository.ActivityTaskRepository;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central workflow orchestrator.
 *
 * <p>Flow control is driven by the Strategy pattern: every NodeType is handled by
 * the matching {@link NodeEvaluator} bean. Spring injects all evaluators as a list;
 * {@code advanceTo} picks the one that returns {@code true} from {@code supports()}.
 *
 * <p><strong>Tech debt:</strong> {@code ProcessInstance.currentNodeId} is a single
 * {@code String}. When FORK spawns parallel branches the field holds the last-written
 * branch node. Parallel state is tracked solely via {@code ActivityTask} records.
 * Migrate to {@code Set<String> activeNodeIds} when multi-branch visibility matters.
 */
@Service
public class WorkflowEngineImpl implements WorkflowEngine {

    private final BusinessPolicyRepository policyRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final ActivityTaskRepository taskRepository;
    private final List<NodeEvaluator> evaluators;
    private final FlowRouter flowRouter;
    private final SimpMessagingTemplate messagingTemplate;

    public WorkflowEngineImpl(BusinessPolicyRepository policyRepository,
                               ProcessInstanceRepository instanceRepository,
                               ActivityTaskRepository taskRepository,
                               List<NodeEvaluator> evaluators,
                               FlowRouter flowRouter,
                               SimpMessagingTemplate messagingTemplate) {
        this.policyRepository = policyRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.evaluators = evaluators;
        this.flowRouter = flowRouter;
        this.messagingTemplate = messagingTemplate;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public ProcessInstance startProcess(String policyId, String userId, Map<String, Object> initialData) {
        BusinessPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));

        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new PolicyNotActiveException(policyId);
        }

        ProcessInstance instance = new ProcessInstance();
        instance.setBusinessPolicyId(policyId);
        instance.setInitiatedBy(userId);
        instance.setStatus(InstanceStatus.ACTIVE);
        instance.setContextData(initialData != null ? new HashMap<>(initialData) : new HashMap<>());
        instance.setStartedAt(LocalDateTime.now());
        instance = instanceRepository.save(instance); // Persist to obtain MongoDB-generated id

        ActivityNode initialNode = policy.getNodes().stream()
                .filter(n -> n.getType() == NodeType.INITIAL_NODE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Policy has no INITIAL_NODE: " + policyId));

        advanceTo(initialNode, policy, instance);
        return instance;
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> formData, String userId) {
        ActivityTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new InvalidTaskStateException("Task is already completed: " + taskId);
        }

        ProcessInstance instance = instanceRepository.findById(task.getProcessInstanceId())
                .orElseThrow(() -> new IllegalStateException(
                        "ProcessInstance not found: " + task.getProcessInstanceId()));

        BusinessPolicy policy = policyRepository.findById(instance.getBusinessPolicyId())
                .orElseThrow(() -> new PolicyNotFoundException(instance.getBusinessPolicyId()));

        // Merge formData into contextData BEFORE evaluating the next node,
        // so that DECISION guards can read the submitted values.
        if (formData != null && !formData.isEmpty()) {
            if (instance.getContextData() == null) {
                instance.setContextData(new HashMap<>(formData));
            } else {
                instance.getContextData().putAll(formData);
            }
        }

        // Complete the task
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        if (formData != null) {
            task.setFormData(formData);
        }
        taskRepository.save(task);
        instanceRepository.save(instance); // Persist merged contextData

        // Advance to the nodes AFTER this ACTION node — do NOT re-evaluate the
        // ActionEvaluator (it would create a duplicate task).
        ActivityNode actionNode = flowRouter.findNodeById(task.getNodeId(), policy)
                .orElseThrow(() -> new IllegalStateException("Node not found: " + task.getNodeId()));

        for (var flow : flowRouter.getOutgoingFlows(actionNode.getId(), policy)) {
            ActivityNode nextNode = flowRouter.findNodeById(flow.getTargetNodeId(), policy)
                    .orElseThrow(() -> new IllegalStateException(
                            "Next node not found: " + flow.getTargetNodeId()));
            instance.setCurrentNodeId(flow.getTargetNodeId());
            advanceTo(nextNode, policy, instance);
        }

        // WebSocket: push the updated process state to all subscribers (DT-10 / RF-03)
        String currentNodeLabel = flowRouter.findNodeById(instance.getCurrentNodeId(), policy)
                .map(ActivityNode::getLabel)
                .orElse(instance.getCurrentNodeId());

        ProcessStateNotificationDto stateNotification = new ProcessStateNotificationDto(
                instance.getId(),
                instance.getCurrentNodeId(),
                currentNodeLabel,
                policy.getName(),
                instance.getStatus()
        );
        messagingTemplate.convertAndSend("/topic/process/" + instance.getId(), stateNotification);
    }

    // -------------------------------------------------------------------------
    // Internal routing
    // -------------------------------------------------------------------------

    /**
     * Recursively drives execution forward from {@code node}.
     *
     * <ul>
     *   <li>Selects the evaluator for the node's type.</li>
     *   <li>If the evaluator returns an empty list the recursion stops (ACTION
     *       blocks, FLOW_FINAL / ACTIVITY_FINAL terminate; any instance mutations
     *       made by the evaluator are persisted here).</li>
     *   <li>Otherwise {@code currentNodeId} is updated and the method recurses for
     *       each returned nodeId — supporting both sequential and FORK-parallel flows.</li>
     * </ul>
     */
    private void advanceTo(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        NodeEvaluator evaluator = evaluators.stream()
                .filter(e -> e.supports(node.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No evaluator registered for NodeType: " + node.getType()));

        List<String> nextNodeIds = evaluator.evaluate(node, policy, instance);

        if (nextNodeIds.isEmpty()) {
            // Terminal or blocking node. Persist any mutations the evaluator applied
            // to the instance (e.g. ACTIVITY_FINAL sets status = COMPLETED).
            instanceRepository.save(instance);
            return;
        }

        for (String nextNodeId : nextNodeIds) {
            ActivityNode nextNode = flowRouter.findNodeById(nextNodeId, policy)
                    .orElseThrow(() -> new IllegalStateException(
                            "Next node not found in policy: " + nextNodeId));
            instance.setCurrentNodeId(nextNodeId);
            advanceTo(nextNode, policy, instance);
        }

        instanceRepository.save(instance);
    }
}

