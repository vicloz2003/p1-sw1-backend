package com.ibpms.engine.router;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pure routing utility — resolves nodes, flows and partitions from the embedded
 * BusinessPolicy graph. Stateless; no MongoDB access.
 */
@Component
public class FlowRouter {

    public List<ControlFlow> getOutgoingFlows(String nodeId, BusinessPolicy policy) {
        return policy.getFlows().stream()
                .filter(f -> nodeId.equals(f.getSourceNodeId()))
                .collect(Collectors.toList());
    }

    public List<ControlFlow> getIncomingFlows(String nodeId, BusinessPolicy policy) {
        return policy.getFlows().stream()
                .filter(f -> nodeId.equals(f.getTargetNodeId()))
                .collect(Collectors.toList());
    }

    public Optional<ActivityNode> findNodeById(String nodeId, BusinessPolicy policy) {
        return policy.getNodes().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .findFirst();
    }

    public Optional<ActivityPartition> findPartitionById(String partitionId, BusinessPolicy policy) {
        return policy.getPartitions().stream()
                .filter(p -> partitionId.equals(p.getId()))
                .findFirst();
    }
}

