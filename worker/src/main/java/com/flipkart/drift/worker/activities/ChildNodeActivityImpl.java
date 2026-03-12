package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.drift.commons.model.node.ChildNode;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;

public class ChildNodeActivityImpl extends BaseNodeActivityImpl<ChildNode> implements ChildNodeActivity {

    @Inject
    public ChildNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        super(workflowContextHBService);
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<ChildNode> activityRequest) {
        JsonNode evaluatedParams = activityRequest.getContext().get("nodeParameters");
        boolean useEmpty = evaluatedParams == null || evaluatedParams.isNull() || !evaluatedParams.isObject();
        return ActivityResponse.builder()
                .nodeResponse(useEmpty ? MAPPER.createObjectNode() : evaluatedParams)
                .workflowStatus(WorkflowStatus.RUNNING)
                .build();
    }
}
