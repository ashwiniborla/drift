package com.flipkart.drift.api.service;

import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.filters.RequestThreadContext;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowResumeResponse;
import com.flipkart.drift.sdk.model.response.WorkflowStartResponse;
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
    private final Utility utility;
    private final DriftConfiguration driftConfiguration;

    @Inject
    public TemporalService(DriftConfiguration driftConfiguration,
                           Utility utility) {
        this.stubsOptions = WorkflowServiceStubsOptions
                .newBuilder()
                .setTarget(driftConfiguration.getTemporalFrontEnd())
                .build();
        this.serviceStub = WorkflowServiceStubs.newServiceStubs(stubsOptions);
        this.client = WorkflowClient.newInstance(serviceStub);
        this.utility = utility;
        this.driftConfiguration = driftConfiguration;
    }

    public WorkflowStartResponse startWorkflow(WorkflowStartRequest workflowStartRequest) {
        if (workflowStartRequest.getWorkflowId() == null || workflowStartRequest.getWorkflowId().isBlank()) {
            workflowStartRequest.setWorkflowId(utility.generateWorkflowId(null, false));
        }
        workflowStartRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
        return executeWorkflow(workflowStartRequest);
    }

    // PROBE::redis-removal-api-temporal-async::ENTRY
    public WorkflowStartResponse executeWorkflow(WorkflowStartRequest workflowStartRequest) {
        String workflowId = workflowStartRequest.getWorkflowId();
        long _probeStartMs = System.currentTimeMillis();
        log.info("feature=redis-removal op=executeWorkflow workflowId={}", workflowId);
        try {
            GenericWorkflow workflow = client.newWorkflowStub(
                    GenericWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setWorkflowExecutionTimeout(Duration.ofMinutes(1440))
                            .setTaskQueue(driftConfiguration.getTemporalTaskQueue())
                            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING)
                            .build()
            );
            // Non-blocking: WorkflowClient.start() submits the workflow and returns immediately
            WorkflowClient.start(workflow::startWorkflow, workflowStartRequest);
            log.info("feature=redis-removal op=executeWorkflow workflowId={} durationMs={}", workflowId,
                    System.currentTimeMillis() - _probeStartMs);
            // PROBE::redis-removal-api-temporal-async::EXIT
            return WorkflowStartResponse.builder()
                    .workflowId(workflowId)
                    .workflowStatus(WorkflowStatus.CREATED)
                    .build();
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("feature=redis-removal op=executeWorkflow workflowId={} error={}", workflowId, e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to start workflow: " + e.getMessage());
        }
    }

    // PROBE::redis-removal-api-temporal-async::ENTRY
    public WorkflowResumeResponse resumeWorkflow(WorkflowResumeRequest workflowResumeRequest) {
        try {
            log.info("feature=redis-removal op=resumeWorkflow workflowId={}", workflowResumeRequest.getWorkflowId());
            workflowResumeRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowResumeRequest.getWorkflowId());
            workflow.resumeWorkflow(workflowResumeRequest);
            // PROBE::redis-removal-api-temporal-async::EXIT
            return WorkflowResumeResponse.builder()
                    .workflowId(workflowResumeRequest.getWorkflowId())
                    .workflowStatus(WorkflowStatus.RUNNING)
                    .build();
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("feature=redis-removal op=resumeWorkflow workflowId={} error={}", workflowResumeRequest.getWorkflowId(), e.getMessage(), e);
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
}
