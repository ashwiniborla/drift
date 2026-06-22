package com.flipkart.drift.api.service;

import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.filters.RequestThreadContext;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.sdk.model.response.WorkflowResponse;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.api.service.utils.Utility;
import com.flipkart.drift.workflows.GenericWorkflow;
import com.google.inject.Inject;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.*;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.time.Duration;

import static com.flipkart.drift.commons.utils.Constants.Workflow.WORKFLOW_EXCEPTION;


@Slf4j
public class TemporalService {
    private final WorkflowServiceStubsOptions stubsOptions;
    // Create a stub that accesses a Temporal Service
    private final WorkflowServiceStubs serviceStub;
    private final WorkflowClient client;
    private final RedisPubSubService redisPubSubService;
    public static final String START = "start";
    public static final String RESUME = "resume";
    private final Utility utility;
    private final DriftConfiguration driftConfiguration;

    @Inject
    public TemporalService(RedisPubSubService redisPubSubService,
                           DriftConfiguration driftConfiguration,
                           Utility utility) {
        this.stubsOptions = WorkflowServiceStubsOptions
                .newBuilder()
                .setTarget(driftConfiguration.getTemporalFrontEnd())
                .build();
        this.serviceStub = WorkflowServiceStubs.newServiceStubs(stubsOptions);
        this.redisPubSubService = redisPubSubService;
        this.client = WorkflowClient.newInstance(serviceStub);
        this.utility = utility;
        this.driftConfiguration = driftConfiguration;
    }

    public WorkflowResponse startWorkflow(WorkflowStartRequest workflowStartRequest) {
        if (workflowStartRequest.getWorkflowId() == null || workflowStartRequest.getWorkflowId().isBlank()) {
            workflowStartRequest.setWorkflowId(utility.generateWorkflowId(null, false));
        }
        workflowStartRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
        return executeWorkflow(workflowStartRequest);
    }

    public WorkflowResponse executeWorkflow(WorkflowStartRequest workflowStartRequest) {
        String workflowId = workflowStartRequest.getWorkflowId();
        GenericWorkflow workflow;
        try {
            workflow = client.newWorkflowStub(
                    GenericWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setWorkflowExecutionTimeout(Duration.ofMinutes(1440))
                            .setTaskQueue(driftConfiguration.getTemporalTaskQueue())
                            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING)
                            .build()
            );
            redisPubSubService.subscribeAndExecute(workflowId, () -> {
                WorkflowClient.start(workflow::startWorkflow, workflowStartRequest);
                return null;
            }, START);
            return buildResponseAndReturn(workflow);
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during workflow start: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to start workflow: " + e.getMessage());
        }
    }

    public WorkflowResponse resumeWorkflow(WorkflowResumeRequest workflowResumeRequest) {
        try {
            workflowResumeRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowResumeRequest.getWorkflowId());
            redisPubSubService.subscribeAndExecute(workflowResumeRequest.getWorkflowId(), () -> {
                workflow.resumeWorkflow(workflowResumeRequest);
                return null;
            }, RESUME);
            return buildResponseAndReturn(workflow);
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during workflow resume: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to resume workflow: " + e.getMessage());
        }
    }

    public void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowTerminateRequest.getWorkflowId());
            workflow.terminateWorkflow(workflowTerminateRequest);
            WorkflowStub untyped = WorkflowStub.fromTyped(workflow);
            untyped.cancel();
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during workflow termination: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to terminate workflow: " + e.getMessage());
        }
    }

    public WorkflowState getWorkflowState(String workflowId) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowId);
            return workflow.getWorkflowState();
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause().getMessage());
        }
    }

    public WorkflowUtilityResponse executeDisconnectedNode(WorkflowUtilityRequest workflowUtilityRequest) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowUtilityRequest.getWorkflowId());
            workflowUtilityRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
            return workflow.executeDisconnectedNode(workflowUtilityRequest);
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause().getMessage());
        }
    }

    private WorkflowResponse buildResponseAndReturn(GenericWorkflow workflow) {
        WorkflowState workflowState = workflow.getWorkflowState();
        View view = workflowState.getView();
        return WorkflowResponse.builder()
                .disposition(workflowState.getDisposition())
                .errorMessage(workflowState.getErrorMessage())
                .incidentId(workflowState.getIncidentId())
                .workflowId(workflowState.getWorkflowId())
                .workflowStatus(workflowState.getStatus())
                .view(view)
                .build();
    }
}




