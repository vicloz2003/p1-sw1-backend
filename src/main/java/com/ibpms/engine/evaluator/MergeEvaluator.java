package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.engine.router.FlowRouter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MERGE: advances whenever ANY incoming branch arrives — no synchronisation needed.
 * Simply returns all outgoing node IDs (always one in a well-formed diagram).
 */
@Component
public class MergeEvaluator implements NodeEvaluator {

    private final FlowRouter flowRouter;

    public MergeEvaluator(FlowRouter flowRouter) {
        this.flowRouter = flowRouter;
    }

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.MERGE;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        return flowRouter.getOutgoingFlows(node.getId(), policy).stream()
                .map(ControlFlow::getTargetNodeId)
                .collect(Collectors.toList());
    }
}

