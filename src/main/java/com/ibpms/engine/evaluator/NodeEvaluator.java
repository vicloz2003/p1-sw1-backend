package com.ibpms.engine.evaluator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ProcessInstance;
import com.ibpms.domain.enums.NodeType;

import java.util.List;

public interface NodeEvaluator {
    boolean supports(NodeType type);
    List<String> evaluate(ActivityNode node, BusinessPolicy policy, ProcessInstance instance);
}

