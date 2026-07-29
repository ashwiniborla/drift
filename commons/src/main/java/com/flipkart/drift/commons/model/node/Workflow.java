package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.exception.ErrorMessages;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workflow {
    private String id;
    private String comment;
    private String startNode;
    private String version;
    private String defaultFailureNode;
    private Map<String, WorkflowNode> states;
    private List<String> postWorkflowCompletionNodes;

    public void validateWFFields() {
        if (StringUtils.isEmpty(id)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "id can't be empty");
        }
        if (StringUtils.isEmpty(startNode)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "startNode can't be empty");
        }
        if (Objects.isNull(states) || states.isEmpty()) {
            throw new ApiException(Response.Status.BAD_REQUEST, "states can't be null or empty");
        }
        for (Map.Entry<String, WorkflowNode> entry : states.entrySet()) {
            validateNodeRetryAndTimeout(entry.getKey(), entry.getValue());
        }
    }

    private static void validateNodeRetryAndTimeout(String nodeName, WorkflowNode node) {
        if (node == null) {
            return;
        }
        if (node.getTimeoutSeconds() != null && node.getTimeoutSeconds() <= 0) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    String.format(ErrorMessages.TIMEOUT_SECONDS_MUST_BE_POSITIVE, nodeName));
        }
        NodeRetryConfig retryConfig = node.getRetryConfig();
        if (retryConfig == null) {
            return;
        }
        if (retryConfig.getMaxAttempts() != null && retryConfig.getMaxAttempts() < 1) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    String.format(ErrorMessages.MAX_ATTEMPTS_MUST_BE_AT_LEAST_ONE, nodeName));
        }
        if (retryConfig.getInitialIntervalSeconds() <= 0) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    String.format(ErrorMessages.INITIAL_INTERVAL_MUST_BE_POSITIVE, nodeName));
        }
        if (retryConfig.getMaxIntervalSeconds() <= 0) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    String.format(ErrorMessages.MAX_INTERVAL_MUST_BE_POSITIVE, nodeName));
        }
        if (retryConfig.getBackoffCoefficient() < 1.0) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    String.format(ErrorMessages.BACKOFF_COEFFICIENT_MUST_BE_AT_LEAST_ONE, nodeName));
        }
    }
}
