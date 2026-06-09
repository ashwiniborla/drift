package com.flipkart.drift.worker.workflows;

import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.worker.activities.FetchWorkflowActivity;
import com.flipkart.drift.worker.activities.ReturnControlActivity;
import com.flipkart.drift.worker.activities.WorkflowContextManagerActivity;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.worker.temporal.OptionsStore;
import io.temporal.failure.ApplicationFailure;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.Map;

@Data
@Slf4j
public class GenericWorkflowImpl implements com.flipkart.drift.workflows.GenericWorkflow {
    private static final ClassLoader classLoader = GenericWorkflowImpl.class.getClassLoader();
    private final Logger logger = io.temporal.workflow.Workflow.getLogger(GenericWorkflowImpl.class);
    private final WorkflowNodeExecutor nodeExecutor;
    private WorkflowState workflowState;

    public GenericWorkflowImpl() {
        this.workflowState = new WorkflowState();
        this.nodeExecutor = new WorkflowNodeExecutor(workflowState);
    }

    @Override
    @Timed(name = "workflow.start.duration")
    public void startWorkflow(WorkflowStartRequest workflowStartRequest) {
        try {
            initializeWorkflow(workflowStartRequest);
            Workflow workflow = fetchDsl(workflowStartRequest);
            if (workflow == null) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Workflow not found for issue: " + workflowStartRequest.getIssueDetail().getIssueId(),
                        "WORKFLOW_NOT_FOUND"
                );
            }
            WorkflowNode currentNode = workflow.getStates().get(workflow.getStartNode());
            if (currentNode == null) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Start node not found in workflow: " + workflow.getStartNode(),
                        "START_NODE_NOT_FOUND"
                );
            }
            executeWorkflowNodes(workflow, currentNode, workflowStartRequest.getWorkflowId(), workflowStartRequest.getThreadContext(), workflowStartRequest);

        } catch (Exception e) {
            logger.error("Error while executing workflow: {}", e.getMessage(), e);
            this.workflowState.setStatus(WorkflowStatus.FAILED);
            if (this.workflowState.getErrorMessage() == null) {
                this.workflowState.setErrorMessage(e.getMessage());
            }
            if (e instanceof ApplicationFailure) {
                throw e;
            }
            throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "Error while executing workflow: " + e.getMessage(),
                    "WORKFLOW_FAILED", e
            );
        }
    }

    @Override
    @Timed(name = "workflow.resume.duration")
    public void resumeWorkflow(WorkflowResumeRequest workflowResumeRequest) {
        try {
            io.temporal.workflow.Workflow.newActivityStub(WorkflowContextManagerActivity.class, OptionsStore.activityOptions)
                    .resumeWorkflowState(workflowResumeRequest, this.workflowState.getCurrentNodeRef());
            this.workflowState.setStatus(WorkflowStatus.RUNNING);
        } catch (Exception e) {
            logger.error("Error resuming workflow: {}", e.getMessage(), e);
            this.workflowState.setStatus(WorkflowStatus.FAILED);
            this.workflowState.setErrorMessage(e.getMessage());
            throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "Failed to resume workflow: " + e.getMessage(),
                    "RESUME_FAILED", e
            );
        }
    }

    @Override
    public void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest) {
        this.workflowState.setStatus(WorkflowStatus.TERMINATED);
    }


    @Override
    public WorkflowState getWorkflowState() {
        return this.workflowState;
    }

    private void executeWorkflowNodes(Workflow workflow, WorkflowNode currentNode, String workflowId,
                                       Map<String, String> threadContext, WorkflowStartRequest workflowStartRequest) {
        while (currentNode != null) {
            boolean isDefaultFailureNode = workflow.getDefaultFailureNode() != null
                    && currentNode.getInstanceName().equals(workflow.getDefaultFailureNode());

            if (isDefaultFailureNode) {
                try {
                    logger.info("WfId : {} Running defaultFailureNode: {}", workflowId, currentNode.getInstanceName());
                    nodeExecutor.executeNodeWithoutStatusUpdate(currentNode, threadContext);
                } catch (Exception e) {
                    this.workflowState.setErrorMessage("defaultFailureNode failed: " + e.getMessage());
                    throw ApplicationFailure.newNonRetryableFailureWithCause(
                            "defaultFailureNode failed, aborting", "FAILURE_NODE_FAILED", e);
                }
                io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions)
                        .exec(workflowId);
                throw ApplicationFailure.newNonRetryableFailure(
                        "Workflow failed — defaultFailureNode completed", "WORKFLOW_FAILED_VIA_FALLBACK");
            }

            ActivityThinResponse activityThinResponse;
            try {
                logger.info("WfId : {} Running node: {}", workflowId, currentNode.getInstanceName());
                activityThinResponse = nodeExecutor.executeNode(currentNode, threadContext, workflowStartRequest);
            } catch (Exception e) {
                currentNode = nodeExecutor.handleNodeExecutionError(e, currentNode, workflow);
                continue;
            }
            if (activityThinResponse != null) {
                nodeExecutor.handleNodeResponseStatus(workflowId, activityThinResponse, workflow, threadContext);
            }
            currentNode = workflow.getStates().get(currentNode.getNextNode());
        }
    }

    private void initializeWorkflow(WorkflowStartRequest workflowStartRequest) {
        this.workflowState.setIncidentId(workflowStartRequest.getIncidentId());
        this.workflowState.setWorkflowId(io.temporal.workflow.Workflow.getInfo().getWorkflowId());
        this.workflowState.setStatus(WorkflowStatus.CREATED);
        this.workflowState.setIssueDetail(workflowStartRequest.getIssueDetail());
        io.temporal.workflow.Workflow.newActivityStub(WorkflowContextManagerActivity.class, OptionsStore.activityOptions)
                .persistWorkflowState(workflowStartRequest, io.temporal.workflow.Workflow.getInfo().getWorkflowId());
    }

    private Workflow fetchDsl(WorkflowStartRequest workflowRequest) {
        log.info("Fetching workflow DSL for issueId: {}", workflowRequest.getIssueDetail().getIssueId());
        FetchWorkflowActivity fetchWorkflowActivity = io.temporal.workflow.Workflow.newActivityStub(
                FetchWorkflowActivity.class, OptionsStore.activityOptions);
        return fetchWorkflowActivity.fetchWorkflowBasedOnRequest(workflowRequest);

    }

    @Timed(name = "workflow.execute.disconnected.duration")
    @Override
    public WorkflowUtilityResponse executeDisconnectedNode(WorkflowUtilityRequest workflowUtilityRequest) {
        String tenant = workflowUtilityRequest.getThreadContext().getOrDefault("tenant", "fk");
        WorkflowNode workflowNode = io.temporal.workflow.Workflow.newActivityStub(
                FetchWorkflowActivity.class,
                OptionsStore.activityOptions).fetchWorkflowNode(
                this.workflowState.getIssueDetail().getIssueId(),
                workflowUtilityRequest.getNode(), tenant);
        return nodeExecutor.executeWorkflowNode(workflowUtilityRequest, workflowNode);
    }
}