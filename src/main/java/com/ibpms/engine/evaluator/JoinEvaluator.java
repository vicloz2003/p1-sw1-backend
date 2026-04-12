package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.domain.enums.TaskStatus;
import com.ibpms.engine.router.FlowRouter;
import com.ibpms.repository.ActivityTaskRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JOIN: waits until ALL parallel branches that feed into this node have a
 * COMPLETED ActivityTask for their source node.
 * <p>
 * Assumption: each branch ends with at least one ACTION node directly before JOIN,
 * ensuring a persisted ActivityTask exists per branch for the existence check.
 * <p>
 * NOTE: this check is not transactionally atomic in a multi-node setup. For
 * production use, a MongoDB transaction or distributed lock should wrap the
 * read-and-advance logic.
 */
@Component
public class JoinEvaluator implements NodeEvaluator {

    private final FlowRouter flowRouter;
    private final ActivityTaskRepository taskRepository;

    public JoinEvaluator(FlowRouter flowRouter, ActivityTaskRepository taskRepository) {
        this.flowRouter = flowRouter;
        this.taskRepository = taskRepository;
    }

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.JOIN;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        List<ControlFlow> incomingFlows = flowRouter.getIncomingFlows(node.getId(), policy);

        boolean allBranchesComplete = incomingFlows.stream()
                .allMatch(flow -> taskRepository.existsByProcessInstanceIdAndNodeIdAndStatus(
                        instance.getId(), flow.getSourceNodeId(), TaskStatus.COMPLETED));

        if (!allBranchesComplete) {
            return List.of(); // Still waiting — do not advance
        }

        return flowRouter.getOutgoingFlows(node.getId(), policy).stream()
                .map(ControlFlow::getTargetNodeId)
                .collect(Collectors.toList());
    }
}

