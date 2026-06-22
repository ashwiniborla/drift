package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.worker.Utility.WorkerUtility;
import com.flipkart.drift.worker.executor.HttpExecutor;
import com.flipkart.drift.commons.model.clientComponent.HttpComponents;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.node.HttpNode;
import com.flipkart.drift.commons.model.resolvedDetails.HttpDetails;
import com.flipkart.drift.commons.model.resolvedDetails.TransformerDetails;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowConfigStoreService;
import com.flipkart.drift.commons.exception.HttpClientErrorException;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.worker.translator.ClientResolvedDetailBuilder;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;
import static com.flipkart.drift.commons.utils.Constants.PerfConstants.X_PERF_TEST_DASH;
import static com.flipkart.drift.commons.utils.Constants.PerfConstants.X_PERF_TEST_UNDERSCORE;
import static com.flipkart.drift.commons.utils.Constants.Workflow.GLOBAL;
import static com.flipkart.drift.commons.utils.Constants.Workflow.HTTP_RESPONSE;
import static com.flipkart.drift.commons.utils.Constants.Workflow.ENUM_STORE;

@Slf4j
public class HttpNodeNodeActivityImpl extends BaseNodeActivityImpl<HttpNode> implements HttpNodeNodeActivity {

    private final WorkflowConfigStoreService workflowConfigStoreService;

    @Inject
    public HttpNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService, WorkflowConfigStoreService workflowConfigStoreService) {
        super(workflowContextHBService);
        this.workflowConfigStoreService = workflowConfigStoreService;
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<HttpNode> activityRequest) {
        try {
            JsonNode httpResponse = executeHttp(activityRequest.getNodeDefinition().getHttpComponents(),
                    activityRequest.getNodeDefinition().getVersion(),
                    activityRequest.getContext(),
                    activityRequest.getNodeDefinition().getId(), activityRequest.getThreadContext());
            ObjectNode contextWrapper = MAPPER.createObjectNode();
            contextWrapper.set(GLOBAL, activityRequest.getContext());
            contextWrapper.set(ENUM_STORE, MAPPER.valueToTree(workflowConfigStoreService.getEnumMapping()));
            TransformerDetails transformerDetails = ClientResolvedDetailBuilder
                    .evaluateGroovy(activityRequest.getNodeDefinition().getTransformerComponents(),
                            activityRequest.getNodeDefinition().getVersion(),
                            contextWrapper.set(HTTP_RESPONSE, httpResponse),
                            TransformerDetails.class);
            return ActivityResponse.builder()
                    .workflowStatus(WorkflowStatus.RUNNING)
                    .nodeResponse(transformerDetails.getTransformedResponse()).build();
        } catch (HttpClientErrorException e) {
            // 4xx: non-retryable — retrying an identical request to a service that rejected it will not succeed
            log.error("HTTP 4xx error executing http node, status={}: {}", e.getStatusCode(), e.getMessage());
            throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "HTTP 4xx: " + e.getMessage(), "HTTP_CLIENT_ERROR", e);
        } catch (Exception e) {
            log.error("Exception while executing http node {}", e.getMessage());
            throw Activity.wrap(e);
        }
    }

    private JsonNode executeHttp(HttpComponents components, String componentVersion, JsonNode workflowContext, String apiIdentifier,
                                 Map<String, String> threadContext) throws IOException {
        try {
            ObjectNode contextWrapper = MAPPER.createObjectNode();
            contextWrapper.set(ENUM_STORE, MAPPER.valueToTree(workflowConfigStoreService.getEnumMapping()));
            HttpDetails httpDetails = ClientResolvedDetailBuilder.evaluateGroovy(components, componentVersion,
                    contextWrapper.set(GLOBAL, workflowContext),
                    HttpDetails.class);
            if (WorkerUtility.shouldAddPerfFlags(threadContext)) {
                Map<String, String> headers = httpDetails.getHeaders();
                if (headers == null) {
                    headers = new HashMap<>();
                    httpDetails.setHeaders(headers);
                }
                headers.put(X_PERF_TEST_UNDERSCORE, Boolean.TRUE.toString());
                headers.put(X_PERF_TEST_DASH, Boolean.TRUE.toString());
            }
            HttpExecutor httpExecutor = HttpExecutor.getInstance(httpDetails.getUrl());
            return httpExecutor.execute(httpDetails, apiIdentifier);
        } catch (Exception e) {
            log.error("HTTP call failed", e);
            throw e;
        }
    }
}
