package com.flipkart.drift.worker.service;

import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.WorkflowContextHBDao;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.worker.model.workflow.WorkflowContext;
import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.google.inject.Inject;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.Stopwatch;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public class WorkflowContextHBService {
    private final WorkflowContextHBDao workflowContextHBDao;


    @Inject
    public WorkflowContextHBService(WorkflowContextHBDao workflowContextHBDao) {
        this.workflowContextHBDao = workflowContextHBDao;
    }

    public void createEntity(WorkflowContext workflowContext, Map<String, String> threadContext) {
        executeWithMetrics("workflow_context_create", () -> {
            try {
                WorkflowContextHB workflowContextHB = new WorkflowContextHB();
                workflowContextHB.setWorkflowId(workflowContext.getWorkflowId());
                workflowContextHB.setContext(workflowContext.getContext());
                workflowContextHBDao.upsert(workflowContextHB, ConnectionType.HOT);
                return null;
            } catch (IOException e) {
                log.error("Error while creating workflow context for workflowId: {}", workflowContext.getWorkflowId(), e);
                throw new ApiException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    public void updateEntity(WorkflowContext workflowContext, Map<String, String> threadContext) {
        executeWithMetrics("workflow_context_update", () -> {
            try {
                WorkflowContextHB workflowContextHB = new WorkflowContextHB();
                workflowContextHB.setWorkflowId(workflowContext.getWorkflowId());
                workflowContextHB.setContext(workflowContext.getContext());
                workflowContextHBDao.update(workflowContextHB, workflowContextHB.getWorkflowId(), ConnectionType.HOT);
                return null;
            } catch (IOException e) {
                log.error("Error while updating workflow context for workflowId: {}", workflowContext.getWorkflowId(), e);
                throw new ApiException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    public WorkflowContextHB getEntityById(String workflowId, Map<String, String> threadContext) {
        return executeWithMetrics("workflow_context_get", () -> {
            try {
                return workflowContextHBDao.get(workflowId, ConnectionType.HOT);
            } catch (IOException e) {
                log.error("Error while fetching workflow context for workflowId: {}", workflowId, e);
                throw new ApiException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    private <T> T executeWithMetrics(String metricName, Supplier<T> operation) {
        Scope metricsScope;
        Stopwatch stopwatch = null;
        try {
            try {
                if (Activity.getExecutionContext() != null) {
                    metricsScope = Activity.getExecutionContext().getMetricsScope();
                    if (metricsScope != null) {
                        stopwatch = metricsScope.timer(metricName).start();
                    }
                }
            } catch (IllegalStateException e) {
                // Not in an activity context, so just continue without metrics
            }

            return operation.get();
        } finally {
            if (stopwatch != null) {
                stopwatch.stop();
            }
        }
    }
}