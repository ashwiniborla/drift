package com.flipkart.drift.worker.activities;

import com.flipkart.drift.worker.helper.WorkflowFetchHelper;
import com.flipkart.drift.worker.model.IssueWorkflowMapping;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.worker.service.IssueWorkflowMappingService;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.flipkart.drift.worker.util.Constants.VERSION;
import static com.flipkart.drift.worker.util.Constants.WORKFLOW_ID;

@Slf4j
public class FetchWorkflowActivityImpl implements FetchWorkflowActivity {
    private final WorkflowFetchHelper workflowFetchHelper;
    private final IssueWorkflowMappingService issueWorkflowMappingService;

    @Inject
    public FetchWorkflowActivityImpl(WorkflowFetchHelper workflowFetchHelper,
                                     IssueWorkflowMappingService issueWorkflowMappingService) {
        this.workflowFetchHelper = workflowFetchHelper;
        this.issueWorkflowMappingService = issueWorkflowMappingService;
    }

    @Override
    public Workflow fetchWorkflow(String workflowId, String version, String tenant) {
        return workflowFetchHelper.fetchAndPrepareWorkflow(workflowId, version, tenant);
    }


   @Override
   public Workflow fetchWorkflowBasedOnIssueId(String issueId, String tenant) {
       IssueWorkflowMapping issueWorkflowMapping = issueWorkflowMappingService.getIssueWorkflowMappingForIssue(issueId);
        if (issueWorkflowMapping == null || !issueWorkflowMapping.hasValidConfig()) {
            log.error("No workflow mapping found for issue id: {}", issueId);
            throw Activity.wrap(new RuntimeException("No workflow mapping found for issue id: " + issueId));
        }
        return fetchWorkflow(issueWorkflowMapping.getWorkflowId(), issueWorkflowMapping.getDefaultVersion(), tenant);
    }


    @Override
    public Workflow fetchWorkflowBasedOnRequest(WorkflowStartRequest request) {
        String issueId = request.getIssueDetail().getIssueId();
        String tenant = request.getThreadContext().getOrDefault("tenant", "fk");
        if (request.getParams() != null) {
            Map<String, Object> params = request.getParams();
            Object workflowId = params.get(WORKFLOW_ID);
            Object version = params.get(VERSION);
            if (workflowId != null && version != null) {
                return fetchWorkflow(workflowId.toString(), version.toString(), tenant);
            }
        }

        IssueWorkflowMapping issueWorkflowMapping = issueWorkflowMappingService.getIssueWorkflowMappingForIssue(issueId);
        if (issueWorkflowMapping == null || !issueWorkflowMapping.hasValidConfig()) {
            throw Activity.wrap(new RuntimeException(
                "No workflow mapping found for issue id: " + issueId + 
                ". Please configure workflow mapping."
            ));
        }
        
        boolean isAEnabled = issueWorkflowMapping.isAbEnabled();
        if (!isAEnabled) {
            return fetchWorkflow(issueWorkflowMapping.getWorkflowId(), issueWorkflowMapping.getDefaultVersion(), tenant);
        }
        Map<String, String> abRequestMapping = issueWorkflowMappingService.getWorkflowMapping(request, issueWorkflowMapping);
        if (abRequestMapping == null) {
            throw Activity.wrap(new RuntimeException("No workflow mapping found for issue id when AB is enabled: " + issueId));
        }
        return fetchWorkflow(abRequestMapping.get(WORKFLOW_ID), abRequestMapping.get(VERSION), tenant);
    }

    @Override
    public WorkflowNode fetchWorkflowNode(String issueId, String nodeName, String tenant) {
        Workflow workflow = fetchWorkflowBasedOnIssueId(issueId, tenant);
        return workflow.getStates().get(nodeName);
    }
}
