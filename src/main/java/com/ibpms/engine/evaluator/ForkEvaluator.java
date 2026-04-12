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
 * FORK: activates ALL outgoing branches simultaneously.
 * WorkflowEngineImpl will call advanceTo() for each returned nodeId in sequence,
 * spawning independent execution paths until each hits an ACTION (which blocks).
 * <p>
 * Tech debt: ProcessInstance.currentNodeId is a single String and will hold the
 * last-written branch node. Parallel state is tracked via ActivityTask records,
 * not currentNodeId.
 */
@Component
public class ForkEvaluator implements NodeEvaluator {

    private final FlowRouter flowRouter;

    public ForkEvaluator(FlowRouter flowRouter) {
        this.flowRouter = flowRouter;
    }

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.FORK;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        return flowRouter.getOutgoingFlows(node.getId(), policy).stream()
                .map(ControlFlow::getTargetNodeId)
                .collect(Collectors.toList());
    }
}

