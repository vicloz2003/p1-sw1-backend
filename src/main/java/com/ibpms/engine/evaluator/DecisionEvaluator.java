package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.engine.router.FlowRouter;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DECISION: evaluates each outgoing ControlFlow's guardCondition with SpEL
 * against ProcessInstance.contextData and activates the first truthy branch.
 * A flow with no guard condition is treated as the default/else branch.
 */
@Component
public class DecisionEvaluator implements NodeEvaluator {

    private final FlowRouter flowRouter;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    public DecisionEvaluator(FlowRouter flowRouter) {
        this.flowRouter = flowRouter;
    }

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.DECISION;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("data", instance.getContextData());

        return flowRouter.getOutgoingFlows(node.getId(), policy).stream()
                .filter(flow -> evaluateGuard(flow, ctx))
                .map(ControlFlow::getTargetNodeId)
                .limit(1)
                .toList();
    }

    private boolean evaluateGuard(ControlFlow flow, StandardEvaluationContext ctx) {
        String guard = flow.getGuardCondition();
        if (guard == null || guard.isBlank()) {
            return true; // No guard = default / else branch
        }
        Boolean result = spelParser.parseExpression(guard).getValue(ctx, Boolean.class);
        return Boolean.TRUE.equals(result);
    }
}

