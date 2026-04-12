package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.NodeType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ACTIVITY_FINAL: marks the ProcessInstance as COMPLETED.
 * The instance is mutated here; WorkflowEngineImpl persists it after this returns.
 */
@Component
public class ActivityFinalEvaluator implements NodeEvaluator {

    @Override
    public boolean supports(NodeType type) {
        return type == NodeType.ACTIVITY_FINAL;
    }

    @Override
    public List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance) {
        instance.setStatus(InstanceStatus.COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());
        return List.of(); // Terminal — WorkflowEngineImpl will persist the mutation
    }
}

