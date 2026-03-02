package com.flipkart.drift.worker.helper;

import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.utils.ObjectMapperUtil;
import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.persistence.cache.WorkflowCache;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

/**
 * Single place for "fetch workflow from cache, deep-copy, enrich with node definitions".
 * Used by {@link WorkflowFetchHelper} and {@link com.flipkart.drift.worker.service.SubWorkflowFlattener} to avoid duplicate logic.
 */
@Slf4j
public class WorkflowEnrichHelper {
    private final WorkflowCache workflowCache;
    private final NodeDefinitionCache nodeDefinitionCache;

    @Inject
    public WorkflowEnrichHelper(WorkflowCache workflowCache, NodeDefinitionCache nodeDefinitionCache) {
        this.workflowCache = workflowCache;
        this.nodeDefinitionCache = nodeDefinitionCache;
    }

    /**
     * Load workflow from cache, deep-copy (to avoid mutating cache), enrich each state with NodeDefinition.
     * Does not flatten sub-workflows.
     */
    public Workflow fetchEnrichedCopy(String workflowId, String version, String tenant) {
        Workflow cached = workflowCache.get(workflowId, version, tenant)
                .orElseThrow(() -> Activity.wrap(new RuntimeException(
                        "Workflow not found in cache: id=" + workflowId + ", version=" + version + ", tenant=" + tenant)));
        Workflow workflow = deepCopy(cached);
        enrichWithNodeDefinitions(workflow, tenant);
        return workflow;
    }

    private void enrichWithNodeDefinitions(Workflow workflow, String tenant) {
        for (WorkflowNode node : workflow.getStates().values()) {
            NodeDefinition def = nodeDefinitionCache.get(node.getResourceId(), node.getResourceVersion(), tenant)
                    .orElseThrow(() -> Activity.wrap(new RuntimeException(
                            "NodeDefinition not found in cache: resourceId=" + node.getResourceId()
                                    + ", resourceVersion=" + node.getResourceVersion() + ", tenant=" + tenant)));
            node.setNodeDefinition(deepCopyNodeDefinition(def));
        }
    }

    /**
     * Deep-copy via Jackson convertValue (no JSON string round-trip).
     * Handles all NodeDefinition subtypes (HTTP, GROOVY, BRANCH, SUB_WORKFLOW, WAIT, etc.) and nested
     * polymorphic types (e.g. WaitConfig, ComponentDetail, BranchComponents) via @JsonSubTypes.
     */
    private NodeDefinition deepCopyNodeDefinition(NodeDefinition d) {
        if (d == null) {
            throw new IllegalArgumentException("NodeDefinition to copy must not be null");
        }
        try {
            return ObjectMapperUtil.INSTANCE.getMapper().convertValue(d, NodeDefinition.class);
        } catch (Exception e) {
            throw Activity.wrap(new RuntimeException("Failed to deep copy node definition: " + e.getMessage(), e));
        }
    }

    /**
     * Deep-copy via Jackson convertValue (no JSON string round-trip).
     * Handles Workflow structure including Map&lt;String, WorkflowNode&gt;, and nested NodeDefinition
     * polymorphism in each WorkflowNode (all subtypes preserved).
     */
    private Workflow deepCopy(Workflow workflow) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow to copy must not be null");
        }
        try {
            return ObjectMapperUtil.INSTANCE.getMapper().convertValue(workflow, Workflow.class);
        } catch (Exception e) {
            throw Activity.wrap(new RuntimeException("Failed to deep copy workflow: " + e.getMessage(), e));
        }
    }
}
