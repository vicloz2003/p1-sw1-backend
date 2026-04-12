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
 * INITIAL_NODE: advances immediately to every outgoing node (always one in practice).
 */
@Component
public class InitialNodeEvaluator implements NodeEvaluator {

    private final FlowRouter flowRouter;

    public InitialNodeEvaluator(FlowRouter flowRouter) {
        this.flowRouter = flowRouter;
    }

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.INITIAL_NODE;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        return flowRouter.getOutgoingFlows(node.getId(), policy).stream()
                .map(ControlFlow::getTargetNodeId)
                .collect(Collectors.toList());
    }
}

