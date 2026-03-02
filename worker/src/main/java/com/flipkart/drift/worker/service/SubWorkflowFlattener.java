package com.flipkart.drift.worker.service;

import com.flipkart.drift.commons.model.enums.ErrorHandlingStrategy;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.node.*;
import com.flipkart.drift.worker.helper.WorkflowEnrichHelper;
import com.google.inject.Inject;
import io.temporal.failure.ApplicationFailure;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Flattens SubWorkflowNodes by merging referenced workflows into the parent at fetch time.
 * Uses {@link WorkflowEnrichHelper} to fetch and enrich sub-workflows. Pipeline: find SUB_WORKFLOW nodes → fetch & recurse → merge (scope → add nodes → rewire).
 *
 * <p><b>Error handling strategy:</b> {@link com.flipkart.drift.commons.model.node.SubWorkflowConfig#getErrorHandlingStrategy()}
 * (PROPAGATE vs ISOLATE) is <em>not</em> applied in this flattener. Flattening only merges the node graph; it does not
 * wire failure scopes or inline sub-workflow defaultFailureNodes. At runtime, failure handling currently always uses
 * the root workflow's {@code defaultFailureNode} (i.e. PROPAGATE behaviour). ISOLATE is not implemented: if a
 * sub-workflow is configured with ISOLATE, it is accepted but has no effect.
 */
@Slf4j
public class SubWorkflowFlattener {
    private static final int MAX_DEPTH = 10;

    private final WorkflowEnrichHelper workflowEnrichHelper;

    @Inject
    public SubWorkflowFlattener(WorkflowEnrichHelper workflowEnrichHelper) {
        this.workflowEnrichHelper = workflowEnrichHelper;
    }

    public Workflow flattenWorkflow(Workflow workflow, String tenant) {
        Set<String> usedInstanceNames = new HashSet<>(workflow.getStates().keySet());
        Set<String> usedContextKeys = workflow.getStates().values().stream()
                .map(this::contextKey)
                .collect(Collectors.toSet());

        flattenRecursive(workflow, workflow, tenant, new HashSet<>(), 0, usedInstanceNames, usedContextKeys,
                Collections.singletonList(workflow.getId()));
        return workflow;
    }

    // --- Recursion: find SUB_WORKFLOW nodes, fetch, recurse, then merge ---
    // root: the top-level workflow being flattened; duplicate checks are only applied when merging into root.

    private void flattenRecursive(Workflow root, Workflow parent, String tenant, Set<String> visited, int depth,
                                  Set<String> usedInstanceNames, Set<String> usedContextKeys,
                                  List<String> path) {
        if (depth > MAX_DEPTH) {
            fail("SUB_WORKFLOW_MAX_DEPTH_EXCEEDED",
                    "SubWorkflow nesting depth exceeded maximum of " + MAX_DEPTH + ". Path: " + pathStr(path));
        }

        List<Map.Entry<String, WorkflowNode>> toExpand = parent.getStates().entrySet().stream()
                .filter(e -> isSubWorkflow(e.getValue()))
                .collect(Collectors.toList());

        for (Map.Entry<String, WorkflowNode> e : toExpand) {
            String subNodeName = e.getKey();
            WorkflowNode subNode = e.getValue();
            SubWorkflowNode subDef = (SubWorkflowNode) subNode.getNodeDefinition();
            String subWorkflowId = subDef.getSubWorkflowId();
            String subWorkflowVersion = subDef.getSubWorkflowVersion();
            SubWorkflowConfig config = subDef.getEffectiveConfig();
            if (config.getErrorHandlingStrategy() == ErrorHandlingStrategy.ISOLATE) {
                log.warn("SubWorkflow '{}' has errorHandlingStrategy=ISOLATE which is not implemented; behaviour is PROPAGATE (root workflow's defaultFailureNode). Path: {} → {}", pathStr(path), subWorkflowId);
            }
            // No code path uses config.getErrorHandlingStrategy() for routing; see class Javadoc.

            if (visited.contains(subWorkflowId)) {
                fail("SUB_WORKFLOW_CIRCULAR_REFERENCE",
                        "Circular sub-workflow reference: " + subWorkflowId + ". Path: " + pathStr(path));
            }
            visited.add(subWorkflowId);
            List<String> childPath = new ArrayList<>(path);
            childPath.add(subWorkflowId);

            Workflow subWorkflow = fetchAndEnrich(subWorkflowId, subWorkflowVersion, tenant, childPath);
            flattenRecursive(root, subWorkflow, tenant, visited, depth + 1, usedInstanceNames, usedContextKeys, childPath);

            mergeSubWorkflow(root, parent, subNodeName, subNode, subWorkflow, config,
                    usedInstanceNames, usedContextKeys, childPath);

            visited.remove(subWorkflowId);
        }
    }

    private boolean isSubWorkflow(WorkflowNode n) {
        return n.getNodeDefinition() != null && n.getNodeDefinition().getType() == NodeType.SUB_WORKFLOW;
    }

    private Workflow fetchAndEnrich(String workflowId, String version, String tenant, List<String> path) {
        try {
            return workflowEnrichHelper.fetchEnrichedCopy(workflowId, version, tenant);
        } catch (Exception ex) {
            fail("SUB_WORKFLOW_FETCH_FAILED",
                    "Failed to fetch sub-workflow " + workflowId + "@" + version + ". Path: " + pathStr(path) + ". " + ex.getMessage());
        }
        return null; // unreachable
    }

    /**
     * Merge one sub-workflow into the parent: decide what to include (scope), add nodes with duplicate check,
     * rewire chain, then replace all references to the SubWorkflowNode with the effective start.
     * Duplicate checks (usedInstanceNames/usedContextKeys) are only applied when merging into the root workflow,
     * so that nested inlining (e.g. D into C, then C into A) does not treat the same node name as duplicate.
     */
    private void mergeSubWorkflow(Workflow root, Workflow parent, String subNodeName, WorkflowNode subNode, Workflow subWorkflow,
                                  SubWorkflowConfig config, Set<String> usedInstanceNames, Set<String> usedContextKeys,
                                  List<String> path) {
        InlineScope scope = InlineScope.compute(subWorkflow, config);
        boolean mergingIntoRoot = (parent == root);

        if (scope.nodesToInclude.isEmpty()) {
            replaceAllReferences(parent, subNodeName, subNode.getNextNode());
            removeSubWorkflowNode(parent, subNodeName, subNode, mergingIntoRoot, usedInstanceNames, usedContextKeys);
            return;
        }

        Map<String, WorkflowNode> subStates = subWorkflow.getStates();
        for (String name : scope.nodesToInclude) {
            WorkflowNode node = subStates.get(name);
            if (mergingIntoRoot) {
                checkAndRegisterDuplicate(name, node, path, usedInstanceNames, usedContextKeys);
            }
            parent.getStates().put(name, node);
        }

        rewireChainEnd(parent, scope.effectiveEnd, subNode.getNextNode(), subNode.isEnd());
        mergePostCompletionNodes(parent, subWorkflow, scope.nodesToInclude);

        replaceAllReferences(parent, subNodeName, scope.effectiveStart);
        if (Objects.equals(parent.getStartNode(), subNodeName)) {
            parent.setStartNode(scope.effectiveStart);
        }
        removeSubWorkflowNode(parent, subNodeName, subNode, mergingIntoRoot, usedInstanceNames, usedContextKeys);
    }

    private void checkAndRegisterDuplicate(String instanceName, WorkflowNode node, List<String> path,
                                            Set<String> usedInstanceNames, Set<String> usedContextKeys) {
        String ctxKey = contextKey(node);
        if (usedInstanceNames.contains(instanceName)) {
            fail("SUB_WORKFLOW_DUPLICATE_NODE_NAME",
                    "Duplicate node name: '" + instanceName + "' already exists. Path: " + pathStr(path) + " → node '" + instanceName + "'.");
        }
        if (usedContextKeys.contains(ctxKey)) {
            fail("SUB_WORKFLOW_DUPLICATE_CONTEXT_KEY",
                    "Duplicate context key: '" + ctxKey + "' already in use. Path: " + pathStr(path) + " → node '" + instanceName + "'.");
        }
        usedInstanceNames.add(instanceName);
        usedContextKeys.add(ctxKey);
    }

    private void rewireChainEnd(Workflow parent, String effectiveEnd, String nextNode, boolean end) {
        if (effectiveEnd == null) return;
        WorkflowNode endNode = parent.getStates().get(effectiveEnd);
        if (endNode != null) {
            endNode.setNextNode(nextNode);
            endNode.setEnd(end);
        }
    }

    private void mergePostCompletionNodes(Workflow parent, Workflow subWorkflow, Set<String> inlinedNames) {
        if (subWorkflow.getPostWorkflowCompletionNodes() == null) return;
        if (parent.getPostWorkflowCompletionNodes() == null) {
            parent.setPostWorkflowCompletionNodes(new ArrayList<>());
        }
        for (String name : subWorkflow.getPostWorkflowCompletionNodes()) {
            if (inlinedNames.contains(name) && !parent.getPostWorkflowCompletionNodes().contains(name)) {
                parent.getPostWorkflowCompletionNodes().add(name);
            }
        }
    }

    /**
     * Replace every reference to fromNodeName with toNodeName (nextNode, BranchNode choices/defaultNode, ProcessorNode instructionNodeRef).
     */
    private void replaceAllReferences(Workflow workflow, String fromNodeName, String toNodeName) {
        for (WorkflowNode node : workflow.getStates().values()) {
            if (Objects.equals(node.getNextNode(), fromNodeName)) {
                node.setNextNode(toNodeName);
            }
            replaceRefInNodeDefinition(node.getNodeDefinition(), fromNodeName, toNodeName);
        }
    }

    private void replaceRefInNodeDefinition(NodeDefinition def, String from, String to) {
        if (def == null) return;
        if (def instanceof BranchNode) {
            BranchNode b = (BranchNode) def;
            if (b.getChoices() != null) {
                b.getChoices().forEach(c -> { if (Objects.equals(c.getNextNode(), from)) c.setNextNode(to); });
            }
            if (Objects.equals(b.getDefaultNode(), from)) b.setDefaultNode(to);
        } else if (def instanceof ProcessorNode) {
            ProcessorNode p = (ProcessorNode) def;
            if (Objects.equals(p.getInstructionNodeRef(), from)) p.setInstructionNodeRef(to);
        }
    }

    private void removeSubWorkflowNode(Workflow parent, String subNodeName, WorkflowNode subNode,
                                       boolean mergingIntoRoot, Set<String> usedInstanceNames, Set<String> usedContextKeys) {
        parent.getStates().remove(subNodeName);
        if (mergingIntoRoot) {
            usedInstanceNames.remove(subNodeName);
            usedContextKeys.remove(contextKey(subNode));
        }
    }

    private String contextKey(WorkflowNode node) {
        return node.getContextOverrideKey() != null ? node.getContextOverrideKey() : node.getInstanceName();
    }

    private static String pathStr(List<String> path) {
        return String.join(" → ", path);
    }

    private static void fail(String type, String message) {
        throw ApplicationFailure.newNonRetryableFailure(message, type);
    }

    /**
     * What to inline from a sub-workflow: which node names to add, and the effective start/end for chaining.
     */
    private static class InlineScope {
        final Set<String> nodesToInclude;
        final String effectiveStart;
        final String effectiveEnd;

        InlineScope(Set<String> nodesToInclude, String effectiveStart, String effectiveEnd) {
            this.nodesToInclude = nodesToInclude;
            this.effectiveStart = effectiveStart;
            this.effectiveEnd = effectiveEnd;
        }

        static InlineScope compute(Workflow subWorkflow, SubWorkflowConfig config) {
            Map<String, WorkflowNode> states = subWorkflow.getStates();
            if (states == null || states.isEmpty()) {
                return new InlineScope(Collections.emptySet(), null, null);
            }

            String startName = subWorkflow.getStartNode();
            WorkflowNode startNode = states.get(startName);
            if (startNode == null) {
                return new InlineScope(Collections.emptySet(), null, null);
            }

            Set<String> exclude = new HashSet<>();
            String effectiveStart = startName;
            if (!config.isIncludeFirstNode() && (startNode.getNodeDefinition() == null
                    || startNode.getNodeDefinition().getType() != NodeType.BRANCH)) {
                exclude.add(startName);
                effectiveStart = startNode.getNextNode();
            }

            String terminal = findTerminal(states);
            String effectiveEnd = terminal;
            if (!config.isIncludeLastNode() && terminal != null) {
                exclude.add(terminal);
                effectiveEnd = findPredecessor(states, terminal);
            }

            Set<String> toInclude = states.keySet().stream().filter(n -> !exclude.contains(n)).collect(Collectors.toSet());

            return new InlineScope(toInclude, effectiveStart, effectiveEnd);
        }

        private static String findTerminal(Map<String, WorkflowNode> states) {
            return states.entrySet().stream()
                    .filter(e -> e.getValue().isEnd() || e.getValue().getNextNode() == null)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        private static String findPredecessor(Map<String, WorkflowNode> states, String target) {
            return states.entrySet().stream()
                    .filter(e -> Objects.equals(e.getValue().getNextNode(), target))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }
}
