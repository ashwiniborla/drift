package com.flipkart.drift.commons.model.enums;

/**
 * Controls how failures inside an inlined sub-workflow are handled at runtime.
 * Used by {@link com.flipkart.drift.commons.model.node.SubWorkflowConfig#getErrorHandlingStrategy()}.
 */
public enum ErrorHandlingStrategy {

    /**
     * <b>PROPAGATE</b> (default): Failures in the inlined sub-workflow are treated as failures of the parent.
     * <ul>
     *   <li>When any node inside the inlined sub-workflow fails, the workflow uses the <b>parent workflow's</b>
     *       {@code defaultFailureNode} to route to the failure handler.</li>
     *   <li>There is no separate failure scope for the sub-workflow; the whole run is one failure domain.</li>
     * </ul>
     * <b>Current implementation:</b> No code path reads this value. The executor
     * ({@code WorkflowNodeExecutor.handleNodeExecutionError}) always uses {@code workflow.getDefaultFailureNode()};
     * after flattening, the workflow is the root, so its single {@code defaultFailureNode} is used. That behaviour
     * is exactly PROPAGATE, so no special handling is required for this strategy.
     */
    PROPAGATE,

    /**
     * <b>ISOLATE</b>: Failures in the inlined sub-workflow are first handled by the sub-workflow's own failure node.
     * <ul>
     *   <li>When a node inside the inlined sub-workflow fails, the workflow first tries the <b>sub-workflow's</b>
     *       {@code defaultFailureNode} (if defined). That failure node is inlined into the same flattened graph.</li>
     *   <li>If the sub-workflow has no defaultFailureNode, or if the failure occurs outside the sub-workflow scope,
     *       the parent's defaultFailureNode is used (fallback).</li>
     *   <li>Use when the sub-workflow should have its own failure handling (e.g. retry, compensation) before
     *       escalating to the parent.</li>
     * </ul>
     * <b>Current implementation:</b> Not implemented. Setting ISOLATE is accepted in config but runtime behaviour
     * is the same as PROPAGATE (parent's defaultFailureNode only). Future work: inline sub-workflow's
     * defaultFailureNode and use per-sub-workflow failure scope during execution.
     */
    ISOLATE
}
