package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FLOW_FINAL: silently terminates this branch only.
 * The overall ProcessInstance is NOT marked completed.
 */
@Component
public class FlowFinalEvaluator implements NodeEvaluator {

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.FLOW_FINAL;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        return List.of(); // Branch ends here; process continues on other branches
    }
}

