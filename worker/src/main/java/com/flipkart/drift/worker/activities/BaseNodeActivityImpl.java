package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.worker.Utility.NodeParameterEvaluator;
import com.flipkart.drift.worker.model.activity.ActivityThinRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.worker.model.workflow.WorkflowContext;
import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.commons.utils.ObjectMapperUtil;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public abstract class BaseNodeActivityImpl<T extends NodeDefinition> implements INodeActivity<T> {

    private final WorkflowContextHBService workflowContextHBService;

    protected BaseNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        this.workflowContextHBService = workflowContextHBService;
    }

    // Wrapper method for context management
    public final ActivityThinResponse execute(ActivityThinRequest<T> activityThinRequest) {
        // Execute common steps 1-3
        ActivityResponse response = executeWithContextManagement(activityThinRequest);

        // Step 6: Handle special response types
        View currentView = null;
        String disposition = null;
        if (NodeType.INSTRUCTION.equals(activityThinRequest.getNodeDefinition().getType())) {
            try {
                currentView = ObjectMapperUtil.INSTANCE.getObj(response.getNodeResponse().toString(), View.class);
                disposition = response.getDisposition();
            } catch (IOException e) {
                throw Activity.wrap(new RuntimeException("Error while creating response view for workflowId: " + activityThinRequest.getWorkflowId() + " Node id: " + activityThinRequest.getNodeDefinition().getId()));
            }
        }

        // Step 7: Return thin response
        return ActivityThinResponse.builder()
                .workflowStatus(response.getWorkflowStatus())
                .nextNode(response.getNextNode())
                .disposition(disposition)
                .view(currentView)
                .build();
    }

    // Fat response wrapper method that returns complete ActivityResponse
    public final ActivityResponse executeWithFatResponse(ActivityThinRequest<T> activityThinRequest) {
        // Execute common steps 1-5 and return fat response
        return executeWithContextManagement(activityThinRequest);
    }

    // Private helper method containing common steps 1-3
    private ActivityResponse executeWithContextManagement(ActivityThinRequest<T> activityThinRequest) {
        String workflowId = activityThinRequest.getWorkflowId();

        // Step 1: Fetch context from HBase
        WorkflowContextHB context = workflowContextHBService.getEntityById(workflowId, activityThinRequest.getThreadContext());
        if (context == null) {
            throw Activity.wrap(new RuntimeException("Workflow context not found for ID: " + workflowId));
        }

        ActivityRequest<T> activityRequest = new ActivityRequest<>();
        activityRequest.setIsTerminal(activityThinRequest.getWorkflowNode().isEnd());
        activityRequest.setWorkflowId(workflowId);
        activityRequest.setThreadContext(activityThinRequest.getThreadContext());
        updateContextWithNodeParameters(context.getContext(), activityThinRequest.getWorkflowNode().getParameters());
        activityRequest.setContext(context.getContext());
        activityRequest.setNodeDefinition(activityThinRequest.getNodeDefinition());
        // Step 2: Execute the actual logic
        ActivityResponse response = executeNode(activityRequest);
        // Step 3: Persist updated context
        workflowContextHBService.updateEntity(WorkflowContext.builder()
                .workflowId(workflowId)
                .context(context.getContext().putPOJO(activityThinRequest.getWorkflowNode().getNodeIdentifier(), response.getNodeResponse()))
                .build(), activityThinRequest.getThreadContext());

        return response;
    }

    private void updateContextWithNodeParameters(ObjectNode context,
                                                 Map<String, String> parameters) {
        ObjectNode nodeParameters = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);
        context.set("nodeParameters", nodeParameters);
    }

}