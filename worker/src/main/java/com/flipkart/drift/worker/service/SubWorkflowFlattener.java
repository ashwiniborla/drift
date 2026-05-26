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
 * Uses {@link WorkflowEnrichHelper} to fetch and enrich sub-workflows.
 * Pipeline: find SUB_WORKFLOW nodes → fetch & recurse → merge (classify → add nodes → rewire).
 *
 * <p><b>Node classification during merge:</b> every node in the sub-workflow is placed into exactly
 * one of three buckets:
 * <ol>
 *   <li><b>Failure node</b> ({@code defaultFailureNode}): always excluded and all references to it
 *       are redirected to the root workflow's {@code defaultFailureNode}.</li>
 *   <li><b>Terminal nodes</b> (end=true, or nextNode=null and not a BranchNode): included or excluded
 *       based on {@link SubWorkflowConfig#isIncludeLastNode()}. When included, each terminal is wired
 *       directly to the parent's continuation. When excluded, all references to each terminal are swept
 *       and redirected to the parent's continuation.</li>
 *   <li><b>Regular nodes</b>: always included.</li>
 * </ol>
 *
 * <p><b>Error handling strategy:</b> {@link SubWorkflowConfig#getErrorHandlingStrategy()}
 * (PROPAGATE vs ISOLATE). Under PROPAGATE (default), the sub-workflow's {@code defaultFailureNode} is
 * always excluded and references to it are redirected to the root's {@code defaultFailureNode}. This
 * ensures a single failure domain for the entire flattened workflow.
 * ISOLATE is not yet implemented — if configured, a warning is logged and behaviour falls back to PROPAGATE.
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
        Set<String> rootNodeNames = new HashSet<>(workflow.getStates().keySet());

        flattenRecursively(
                workflow,
                workflow,
                tenant,
                new HashSet<>(),
                0,
                rootNodeNames,
                Collections.singletonList(workflow.getId())
        );

        return workflow;
    }

    // --- Recursion: find SUB_WORKFLOW nodes in currentWorkflow, fetch child, recurse, then merge ---
    // rootWorkflow: the top-level workflow being flattened; duplicate checks are only applied when merging into root.

    private void flattenRecursively(Workflow rootWorkflow,
                                    Workflow currentWorkflow,
                                    String tenant,
                                    Set<String> visitedWorkflowIds,
                                    int depth,
                                    Set<String> rootNodeNames,
                                    List<String> path) {
        validateDepth(depth, path);

        List<Map.Entry<String, WorkflowNode>> subWorkflowEntries = findSubWorkflowEntries(currentWorkflow);
        for (Map.Entry<String, WorkflowNode> entry : subWorkflowEntries) {
            processSubWorkflowEntry(rootWorkflow, currentWorkflow, tenant, entry, visitedWorkflowIds, depth, rootNodeNames, path);
        }
    }

    private List<Map.Entry<String, WorkflowNode>> findSubWorkflowEntries(Workflow workflow) {
        return workflow.getStates().entrySet().stream()
                .filter(entry -> isSubWorkflow(entry.getValue()))
                .collect(Collectors.toList());
    }

    private void processSubWorkflowEntry(Workflow rootWorkflow,
                                         Workflow currentWorkflow,
                                         String tenant,
                                         Map.Entry<String, WorkflowNode> entry,
                                         Set<String> visitedWorkflowIds,
                                         int depth,
                                         Set<String> rootNodeNames,
                                         List<String> path) {
        String subWorkflowNodeName = entry.getKey();
        WorkflowNode subWorkflowNode = entry.getValue();

        SubWorkflowNode subWorkflowDefinition = (SubWorkflowNode) subWorkflowNode.getNodeDefinition();
        SubWorkflowConfig config = subWorkflowDefinition.getConfig();

        warnIfIsolationNotImplemented(subWorkflowNodeName, subWorkflowDefinition, config, path);

        String childWorkflowId = subWorkflowDefinition.getSubWorkflowId();
        String childWorkflowVersion = subWorkflowDefinition.getSubWorkflowVersion();

        validateNoCircularReference(childWorkflowId, visitedWorkflowIds, path);

        // DFS backtracking: add before recursion, remove after merge, so sibling sub-workflows
        // referencing the same child are allowed but cycles within a single branch are not.
        visitedWorkflowIds.add(childWorkflowId);
        List<String> childPath = extendPath(path, childWorkflowId);

        Workflow childWorkflow = fetchAndEnrich(childWorkflowId, childWorkflowVersion, tenant, childPath);

        flattenRecursively(
                rootWorkflow,
                childWorkflow,
                tenant,
                visitedWorkflowIds,
                depth + 1,
                rootNodeNames,
                childPath
        );

        mergeSubWorkflowIntoParent(
                rootWorkflow,
                currentWorkflow,
                subWorkflowNodeName,
                subWorkflowNode,
                childWorkflow,
                config,
                rootNodeNames,
                childPath
        );

        visitedWorkflowIds.remove(childWorkflowId);
    }

    private void validateDepth(int depth, List<String> path) {
        if (depth > MAX_DEPTH) {
            fail(
                    "SUB_WORKFLOW_MAX_DEPTH_EXCEEDED",
                    "SubWorkflow nesting depth exceeded maximum of " + MAX_DEPTH + ". Path: " + pathStr(path)
            );
        }
    }

    private void validateNoCircularReference(String workflowId, Set<String> visitedWorkflowIds, List<String> path) {
        if (visitedWorkflowIds.contains(workflowId)) {
            fail(
                    "SUB_WORKFLOW_CIRCULAR_REFERENCE",
                    "Circular sub-workflow reference: " + workflowId + ". Path: " + pathStr(path)
            );
        }
    }

    // No code path uses config.getErrorHandlingStrategy() for routing; ISOLATE is not implemented.
    private void warnIfIsolationNotImplemented(String subWorkflowNodeName,
                                               SubWorkflowNode subWorkflowDefinition,
                                               SubWorkflowConfig config,
                                               List<String> path) {
        if (config.getErrorHandlingStrategy() == ErrorHandlingStrategy.ISOLATE) {
            log.warn(
                    "SubWorkflow '{}' has errorHandlingStrategy=ISOLATE which is not implemented; " +
                            "behaviour is PROPAGATE (root workflow's defaultFailureNode). Path: {} → {}",
                    subWorkflowNodeName,
                    pathStr(path),
                    subWorkflowDefinition.getSubWorkflowId()
            );
        }
    }

    private Workflow fetchAndEnrich(String workflowId, String version, String tenant, List<String> path) {
        try {
            return workflowEnrichHelper.fetchEnrichedCopy(workflowId, version, tenant);
        } catch (Exception ex) {
            fail(
                    "SUB_WORKFLOW_FETCH_FAILED",
                    "Failed to fetch sub-workflow " + workflowId + "@" + version + ". Path: " + pathStr(path) + ". " + ex.getMessage()
            );
            return null; // unreachable
        }
    }

    /**
     * Merge one sub-workflow into the parent: classify nodes (failure/terminal/regular), inline nodes
     * with duplicate check, rewire failure and exit references, then replace all references to the
     * SubWorkflowNode placeholder with the effective start.
     * Duplicate instance name check (rootNodeNames) is only applied when merging into the root workflow,
     * so that nested inlining (e.g. D into C, then C into A) does not treat the same node name as duplicate.
     */
    private void mergeSubWorkflowIntoParent(Workflow rootWorkflow,
                                            Workflow parentWorkflow,
                                            String subWorkflowNodeName,
                                            WorkflowNode subWorkflowNode,
                                            Workflow childWorkflow,
                                            SubWorkflowConfig config,
                                            Set<String> rootNodeNames,
                                            List<String> path) {
        InliningPlan plan = InliningPlan.compute(childWorkflow, config);

        boolean mergingIntoRoot = parentWorkflow == rootWorkflow;

        if (plan.isEmpty()) {
            removeSubWorkflowAndReconnectParent(parentWorkflow, subWorkflowNodeName, subWorkflowNode, mergingIntoRoot, rootNodeNames);
            return;
        }

        inlineNodes(parentWorkflow, childWorkflow, plan, mergingIntoRoot, rootNodeNames, path);
        rewireFailureReferences(parentWorkflow, plan, rootWorkflow);
        rewireExit(parentWorkflow, subWorkflowNode, plan);
        mergePostCompletionNodes(parentWorkflow, childWorkflow, plan.nodesToInclude);
        replaceSubWorkflowNodeReferences(parentWorkflow, subWorkflowNodeName, plan.effectiveStart);

        if (Objects.equals(parentWorkflow.getStartNode(), subWorkflowNodeName)) {
            parentWorkflow.setStartNode(plan.effectiveStart);
        }

        removeSubWorkflowNode(parentWorkflow, subWorkflowNodeName, mergingIntoRoot, rootNodeNames);
    }

    /**
     * Handles the empty-scope case: the sub-workflow contributed no nodes to inline.
     * Reconnects all parent references that pointed to the SubWorkflowNode to its nextNode instead.
     */
    private void removeSubWorkflowAndReconnectParent(Workflow parentWorkflow,
                                                     String subWorkflowNodeName,
                                                     WorkflowNode subWorkflowNode,
                                                     boolean mergingIntoRoot,
                                                     Set<String> rootNodeNames) {
        if (Objects.equals(parentWorkflow.getStartNode(), subWorkflowNodeName)) {
            parentWorkflow.setStartNode(subWorkflowNode.getNextNode());
        }

        String replacement = subWorkflowNode.getNextNode();
        boolean becomesTerminal = subWorkflowNode.isEnd() && replacement == null;

        for (WorkflowNode node : parentWorkflow.getStates().values()) {
            if (Objects.equals(node.getNextNode(), subWorkflowNodeName)) {
                node.setNextNode(replacement);
                if (becomesTerminal) {
                    node.setEnd(true);
                }
            }
            replaceReferenceInDefinition(node.getNodeDefinition(), subWorkflowNodeName, replacement);
        }

        removeSubWorkflowNode(parentWorkflow, subWorkflowNodeName, mergingIntoRoot, rootNodeNames);
    }

    private void inlineNodes(Workflow parentWorkflow,
                             Workflow childWorkflow,
                             InliningPlan plan,
                             boolean mergingIntoRoot,
                             Set<String> rootNodeNames,
                             List<String> path) {
        Map<String, WorkflowNode> childStates = childWorkflow.getStates();

        for (String nodeName : plan.nodesToInclude) {
            if (mergingIntoRoot) {
                validateAndRegisterRootNodeName(nodeName, path, rootNodeNames);
            }
            parentWorkflow.getStates().put(nodeName, childStates.get(nodeName));
        }
    }

    /**
     * Redirect all references to the excluded sub-workflow defaultFailureNode to the root's
     * defaultFailureNode. Under PROPAGATE, the sub's failure handler is always excluded so that
     * the entire flattened workflow has a single failure domain. When both names are the same,
     * replaceAllReferences is a no-op (from == to), which is correct.
     */
    private void rewireFailureReferences(Workflow parentWorkflow,
                                         InliningPlan plan,
                                         Workflow rootWorkflow) {
        if (plan.excludedFailureNode != null) {
            replaceAllReferences(parentWorkflow, plan.excludedFailureNode, rootWorkflow.getDefaultFailureNode());
        }
    }

    /**
     * Wire every terminal node's exit to the parent's continuation. Two strategies per terminal:
     * <ul>
     *   <li><b>Included terminals</b> (includeLastNode=true): set each terminal's nextNode/end directly.</li>
     *   <li><b>Excluded terminals</b> (includeLastNode=false): sweep every node that references the
     *       excluded terminal (nextNode and BranchNode choices/defaults) and redirect to parent continuation.</li>
     * </ul>
     * Multiple terminals are handled uniformly via loops — no single-terminal assumption.
     */
    private void rewireExit(Workflow parentWorkflow,
                            WorkflowNode subWorkflowNode,
                            InliningPlan plan) {
        for (String terminal : plan.includedTerminals) {
            connectEffectiveEnd(parentWorkflow, terminal, subWorkflowNode.getNextNode(), subWorkflowNode.isEnd());
        }
        for (String terminal : plan.excludedTerminals) {
            replaceTerminalReferences(parentWorkflow, terminal, subWorkflowNode.getNextNode(), subWorkflowNode.isEnd());
        }
    }

    private void replaceTerminalReferences(Workflow workflow,
                                           String terminalNodeName,
                                           String replacement,
                                           boolean endFlag) {
        for (WorkflowNode node : workflow.getStates().values()) {
            if (Objects.equals(node.getNextNode(), terminalNodeName)) {
                node.setNextNode(replacement);
                node.setEnd(endFlag);
            }
            replaceReferenceInDefinition(node.getNodeDefinition(), terminalNodeName, replacement);
        }
    }

    private void connectEffectiveEnd(Workflow workflow,
                                     String effectiveEnd,
                                     String nextNode,
                                     boolean end) {
        if (effectiveEnd == null) {
            return;
        }

        WorkflowNode endNode = workflow.getStates().get(effectiveEnd);
        if (endNode != null) {
            endNode.setNextNode(nextNode);
            endNode.setEnd(end);
        }
    }

    private void replaceSubWorkflowNodeReferences(Workflow workflow,
                                                  String subWorkflowNodeName,
                                                  String replacementStartNode) {
        replaceAllReferences(workflow, subWorkflowNodeName, replacementStartNode);
    }

    private void validateAndRegisterRootNodeName(String nodeName,
                                                 List<String> path,
                                                 Set<String> rootNodeNames) {
        if (rootNodeNames.contains(nodeName)) {
            fail(
                    "SUB_WORKFLOW_DUPLICATE_NODE_NAME",
                    "Duplicate node name: '" + nodeName + "' already exists. Path: " +
                            pathStr(path) + " → node '" + nodeName + "'."
            );
        }
        rootNodeNames.add(nodeName);
    }

    private boolean isSubWorkflow(WorkflowNode node) {
        return node.getNodeDefinition() != null
                && node.getNodeDefinition().getType() == NodeType.SUB_WORKFLOW;
    }

    private void mergePostCompletionNodes(Workflow parentWorkflow,
                                          Workflow childWorkflow,
                                          Set<String> inlinedNodeNames) {
        if (childWorkflow.getPostWorkflowCompletionNodes() == null) {
            return;
        }

        if (parentWorkflow.getPostWorkflowCompletionNodes() == null) {
            parentWorkflow.setPostWorkflowCompletionNodes(new ArrayList<>());
        }

        for (String nodeName : childWorkflow.getPostWorkflowCompletionNodes()) {
            if (inlinedNodeNames.contains(nodeName)
                    && !parentWorkflow.getPostWorkflowCompletionNodes().contains(nodeName)) {
                parentWorkflow.getPostWorkflowCompletionNodes().add(nodeName);
            }
        }
    }

    /**
     * Replace every reference to fromNodeName with toNodeName across all node types:
     * nextNode, BranchNode choices/defaultNode, ProcessorNode instructionNodeRef.
     */
    private void replaceAllReferences(Workflow workflow, String fromNodeName, String toNodeName) {
        for (WorkflowNode node : workflow.getStates().values()) {
            if (Objects.equals(node.getNextNode(), fromNodeName)) {
                node.setNextNode(toNodeName);
            }
            replaceReferenceInDefinition(node.getNodeDefinition(), fromNodeName, toNodeName);
        }
    }

    private void replaceReferenceInDefinition(NodeDefinition definition, String from, String to) {
        if (definition == null) {
            return;
        }

        if (definition instanceof BranchNode) {
            BranchNode branchNode = (BranchNode) definition;

            if (branchNode.getChoices() != null) {
                branchNode.getChoices().forEach(choice -> {
                    if (Objects.equals(choice.getNextNode(), from)) {
                        choice.setNextNode(to);
                    }
                });
            }

            if (Objects.equals(branchNode.getDefaultNode(), from)) {
                branchNode.setDefaultNode(to);
            }
            return;
        }

        if (definition instanceof ProcessorNode) {
            ProcessorNode processorNode = (ProcessorNode) definition;
            if (Objects.equals(processorNode.getInstructionNodeRef(), from)) {
                processorNode.setInstructionNodeRef(to);
            }
        }
    }

    private void removeSubWorkflowNode(Workflow parentWorkflow,
                                       String subWorkflowNodeName,
                                       boolean mergingIntoRoot,
                                       Set<String> rootNodeNames) {
        parentWorkflow.getStates().remove(subWorkflowNodeName);

        if (mergingIntoRoot) {
            rootNodeNames.remove(subWorkflowNodeName);
        }
    }

    private List<String> extendPath(List<String> path, String workflowId) {
        List<String> childPath = new ArrayList<>(path);
        childPath.add(workflowId);
        return childPath;
    }

    private static String pathStr(List<String> path) {
        return String.join(" → ", path);
    }

    private static void fail(String type, String message) {
        throw ApplicationFailure.newNonRetryableFailure(message, type);
    }

    /**
     * Classifies every node in a sub-workflow into three buckets:
     * <ol>
     *   <li>{@code excludedFailureNode}: the sub's defaultFailureNode (always excluded).</li>
     *   <li>{@code includedTerminals} or {@code excludedTerminals}: all terminal nodes
     *       (end=true, or nextNode=null and not a BranchNode), split by includeLastNode config.</li>
     *   <li>{@code nodesToInclude}: everything else (regular nodes + terminals when included).</li>
     * </ol>
     */
    private static class InliningPlan {
        final Set<String> nodesToInclude;
        final String effectiveStart;
        final String excludedFailureNode;     // sub's defaultFailureNode, always excluded (null if none)
        final Set<String> includedTerminals;  // terminals kept in graph (includeLastNode=true)
        final Set<String> excludedTerminals;  // terminals removed from graph (includeLastNode=false)

        InliningPlan(Set<String> nodesToInclude,
                     String effectiveStart,
                     String excludedFailureNode,
                     Set<String> includedTerminals,
                     Set<String> excludedTerminals) {
            this.nodesToInclude = nodesToInclude;
            this.effectiveStart = effectiveStart;
            this.excludedFailureNode = excludedFailureNode;
            this.includedTerminals = includedTerminals;
            this.excludedTerminals = excludedTerminals;
        }

        boolean isEmpty() {
            return nodesToInclude == null || nodesToInclude.isEmpty();
        }

        static InliningPlan compute(Workflow childWorkflow, SubWorkflowConfig config) {
            Map<String, WorkflowNode> states = childWorkflow.getStates();
            if (states == null || states.isEmpty()) {
                return empty();
            }

            String startNodeName = childWorkflow.getStartNode();
            WorkflowNode startNode = states.get(startNodeName);
            if (startNode == null) {
                return empty();
            }

            // A BranchNode cannot be excluded as the first node — it carries routing logic
            // (choices/defaultNode) with no single next-node successor to forward to.
            if (!config.isIncludeFirstNode()
                    && startNode.getNodeDefinition() != null
                    && startNode.getNodeDefinition().getType() == NodeType.BRANCH) {
                fail("SUB_WORKFLOW_INVALID_CONFIG",
                        "Sub-workflow '" + childWorkflow.getId() + "' has a BranchNode as its startNode ('"
                                + startNodeName + "') and includeFirstNode=false. "
                                + "A BranchNode cannot be excluded as the first node.");
            }

            Set<String> excludedNodes = new HashSet<>();
            String effectiveStart = startNodeName;

            if (!config.isIncludeFirstNode()) {
                excludedNodes.add(startNodeName);
                effectiveStart = startNode.getNextNode();
            }

            // Always exclude the sub-workflow's defaultFailureNode under PROPAGATE.
            // The executor uses the root workflow's single defaultFailureNode at runtime;
            // inlining the sub's failure node is redundant and causes duplicate-name errors
            // when both share the same conventional name.
            String childFailureNode = childWorkflow.getDefaultFailureNode();
            if (childFailureNode != null) {
                excludedNodes.add(childFailureNode);
            }

            // Find ALL terminal nodes (not already excluded).
            // A node is terminal if: end=true, OR nextNode=null and it is not a BranchNode.
            // BranchNodes have nextNode=null by design and must not be mistaken for terminals.
            Set<String> allTerminals = findAllTerminalNodes(states, excludedNodes);

            Set<String> includedTerminals = Collections.emptySet();
            Set<String> excludedTerminals = Collections.emptySet();

            if (!allTerminals.isEmpty()) {
                if (config.isIncludeLastNode()) {
                    // Keep terminals in graph; each will be wired to the parent's continuation.
                    includedTerminals = allTerminals;
                } else {
                    // Remove terminals; all references to them are swept and redirected.
                    excludedNodes.addAll(allTerminals);
                    excludedTerminals = allTerminals;
                }
            }

            Set<String> nodesToInclude = states.keySet().stream()
                    .filter(nodeName -> !excludedNodes.contains(nodeName))
                    .collect(Collectors.toSet());

            return new InliningPlan(nodesToInclude, effectiveStart, childFailureNode, includedTerminals, excludedTerminals);
        }

        private static InliningPlan empty() {
            return new InliningPlan(Collections.emptySet(), null, null, Collections.emptySet(), Collections.emptySet());
        }

        private static Set<String> findAllTerminalNodes(Map<String, WorkflowNode> states, Set<String> excludedNodes) {
            return states.entrySet().stream()
                    .filter(entry -> !excludedNodes.contains(entry.getKey()))
                    .filter(entry -> isTerminalNode(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }

        /**
         * A node is terminal if it is marked as end, or has no nextNode and is not a BranchNode.
         * BranchNodes route exclusively through choices/defaultNode; their WorkflowNode.nextNode is
         * null by design and must not be mistaken for a workflow-ending terminal.
         */
        private static boolean isTerminalNode(WorkflowNode node) {
            if (node.isEnd()) {
                return true;
            }

            if (node.getNextNode() != null) {
                return false;
            }

            return node.getNodeDefinition() == null
                    || node.getNodeDefinition().getType() != NodeType.BRANCH;
        }
    }
}
