package com.ibpms.engine.validator;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.enums.NodeType;
import com.ibpms.exception.DiagramInvalidException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates the structural integrity of a {@link BusinessPolicy} diagram
 * before it is published.
 *
 * <p>Rules checked:
 * <ol>
 *   <li>Exactly one {@code INITIAL_NODE}</li>
 *   <li>At least one {@code ACTIVITY_FINAL}</li>
 *   <li>All flow references ({@code sourceNodeId}/{@code targetNodeId}) point to
 *       existing nodes</li>
 *   <li>No orphan nodes — every node (except INITIAL_NODE) has ≥ 1 incoming flow;
 *       every node (except ACTIVITY_FINAL and FLOW_FINAL) has ≥ 1 outgoing flow</li>
 *   <li>{@code FORK} count equals {@code JOIN} count (balanced parallel branches)</li>
 *   <li>{@code DECISION} nodes have ≥ 2 outgoing flows</li>
 *   <li>{@code FORK} nodes have ≥ 2 outgoing flows</li>
 *   <li>Every path from INITIAL_NODE can reach an ACTIVITY_FINAL (no infinite
 *       detached sub-graphs) — checked via reachability from the start node</li>
 * </ol>
 */
@Component
public class DiagramValidator {

    /**
     * Validates the policy diagram. Throws {@link DiagramInvalidException} if any
     * violations are found; does nothing if the diagram is structurally sound.
     */
    public void validate(BusinessPolicy policy) {
        List<String> violations = new ArrayList<>();

        List<ActivityNode> nodes = policy.getNodes();
        List<ControlFlow> flows = policy.getFlows();

        if (nodes == null || nodes.isEmpty()) {
            violations.add("The diagram has no nodes.");
            throw new DiagramInvalidException(violations);
        }

        // ── Index structures ─────────────────────────────────────────────────
        Map<String, ActivityNode> nodeById = nodes.stream()
                .collect(Collectors.toMap(ActivityNode::getId, n -> n, (a, b) -> a));

        Set<String> nodeIds = nodeById.keySet();

        // Incoming / outgoing flow maps
        Map<String, List<ControlFlow>> outgoing = new HashMap<>();
        Map<String, List<ControlFlow>> incoming = new HashMap<>();
        for (String id : nodeIds) {
            outgoing.put(id, new ArrayList<>());
            incoming.put(id, new ArrayList<>());
        }

        // ── Rule 3: valid flow references ────────────────────────────────────
        if (flows != null) {
            for (ControlFlow flow : flows) {
                if (!nodeIds.contains(flow.getSourceNodeId())) {
                    violations.add("Flow '" + flow.getId() + "' references unknown source node '"
                            + flow.getSourceNodeId() + "'.");
                }
                if (!nodeIds.contains(flow.getTargetNodeId())) {
                    violations.add("Flow '" + flow.getId() + "' references unknown target node '"
                            + flow.getTargetNodeId() + "'.");
                }
                if (nodeIds.contains(flow.getSourceNodeId())) {
                    outgoing.computeIfAbsent(flow.getSourceNodeId(), k -> new ArrayList<>()).add(flow);
                }
                if (nodeIds.contains(flow.getTargetNodeId())) {
                    incoming.computeIfAbsent(flow.getTargetNodeId(), k -> new ArrayList<>()).add(flow);
                }
            }
        }

        // ── Rule 1: exactly one INITIAL_NODE ─────────────────────────────────
        List<ActivityNode> initialNodes = nodes.stream()
                .filter(n -> n.getType() == NodeType.INITIAL_NODE)
                .toList();
        if (initialNodes.isEmpty()) {
            violations.add("The diagram must have exactly one INITIAL_NODE, but none was found.");
        } else if (initialNodes.size() > 1) {
            violations.add("The diagram must have exactly one INITIAL_NODE, but "
                    + initialNodes.size() + " were found.");
        }

        // ── Rule 2: at least one ACTIVITY_FINAL ──────────────────────────────
        long activityFinalCount = nodes.stream()
                .filter(n -> n.getType() == NodeType.ACTIVITY_FINAL)
                .count();
        if (activityFinalCount == 0) {
            violations.add("The diagram must have at least one ACTIVITY_FINAL node.");
        }

        // ── Rule 4: orphan nodes ─────────────────────────────────────────────
        Set<NodeType> terminalTypes = Set.of(NodeType.ACTIVITY_FINAL, NodeType.FLOW_FINAL);

        for (ActivityNode node : nodes) {
            String id = node.getId();
            // Every node except INITIAL_NODE needs at least one incoming flow
            if (node.getType() != NodeType.INITIAL_NODE
                    && incoming.getOrDefault(id, List.of()).isEmpty()) {
                violations.add("Node '" + label(node) + "' (" + node.getType()
                        + ") has no incoming flows — it is unreachable.");
            }
            // Every non-terminal node needs at least one outgoing flow
            if (!terminalTypes.contains(node.getType())
                    && outgoing.getOrDefault(id, List.of()).isEmpty()) {
                violations.add("Node '" + label(node) + "' (" + node.getType()
                        + ") has no outgoing flows — it is a dead end.");
            }
        }

        // ── Rule 5: balanced FORK / JOIN ─────────────────────────────────────
        long forkCount = nodes.stream().filter(n -> n.getType() == NodeType.FORK).count();
        long joinCount = nodes.stream().filter(n -> n.getType() == NodeType.JOIN).count();
        if (forkCount != joinCount) {
            violations.add("Unbalanced parallel branches: " + forkCount + " FORK node(s) but "
                    + joinCount + " JOIN node(s). Each FORK must have a corresponding JOIN.");
        }

        // ── Rule 6: DECISION nodes have ≥ 2 outgoing flows ───────────────────
        nodes.stream()
                .filter(n -> n.getType() == NodeType.DECISION)
                .forEach(n -> {
                    int out = outgoing.getOrDefault(n.getId(), List.of()).size();
                    if (out < 2) {
                        violations.add("DECISION node '" + label(n)
                                + "' must have at least 2 outgoing flows but has " + out + ".");
                    }
                });

        // ── Rule 7: FORK nodes have ≥ 2 outgoing flows ───────────────────────
        nodes.stream()
                .filter(n -> n.getType() == NodeType.FORK)
                .forEach(n -> {
                    int out = outgoing.getOrDefault(n.getId(), List.of()).size();
                    if (out < 2) {
                        violations.add("FORK node '" + label(n)
                                + "' must have at least 2 outgoing flows but has " + out + ".");
                    }
                });

        // ── Rule 8: all nodes reachable from INITIAL_NODE ────────────────────
        if (!initialNodes.isEmpty()) {
            Set<String> reachable = reachableFrom(initialNodes.get(0).getId(), outgoing);
            for (ActivityNode node : nodes) {
                if (!reachable.contains(node.getId()) && node.getType() != NodeType.INITIAL_NODE) {
                    violations.add("Node '" + label(node) + "' (" + node.getType()
                            + ") is not reachable from the INITIAL_NODE.");
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new DiagramInvalidException(violations);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** BFS reachability from a start node following outgoing flows. */
    private Set<String> reachableFrom(String startId, Map<String, List<ControlFlow>> outgoing) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (visited.add(current)) {
                for (ControlFlow flow : outgoing.getOrDefault(current, List.of())) {
                    queue.add(flow.getTargetNodeId());
                }
            }
        }
        return visited;
    }

    /** Returns a human-readable identifier for error messages. */
    private String label(ActivityNode node) {
        return (node.getLabel() != null && !node.getLabel().isBlank())
                ? node.getLabel()
                : node.getId();
    }
}
