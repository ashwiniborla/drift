package com.flipkart.drift.worker.helper;

import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.worker.service.SubWorkflowFlattener;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Single place for "fetch workflow, enrich, and flatten sub-workflows".
 * Uses {@link WorkflowEnrichHelper} for fetch+copy+enrich and {@link SubWorkflowFlattener} for flattening.
 */
@Slf4j
public class WorkflowFetchHelper {
    private final WorkflowEnrichHelper workflowEnrichHelper;
    private final SubWorkflowFlattener subWorkflowFlattener;

    @Inject
    public WorkflowFetchHelper(WorkflowEnrichHelper workflowEnrichHelper,
                               SubWorkflowFlattener subWorkflowFlattener) {
        this.workflowEnrichHelper = workflowEnrichHelper;
        this.subWorkflowFlattener = subWorkflowFlattener;
    }

    /**
     * Load workflow (copy + enrich) then flatten SubWorkflowNodes.
     */
    public Workflow fetchAndPrepareWorkflow(String workflowId, String version, String tenant) {
        Workflow workflow = workflowEnrichHelper.fetchEnrichedCopy(workflowId, version, tenant);
        return subWorkflowFlattener.flattenWorkflow(workflow, tenant);
    }
}
