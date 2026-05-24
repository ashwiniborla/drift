package com.flipkart.drift.api.resources;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.service.TemporalService;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowResumeResponse;
import com.flipkart.drift.sdk.model.response.WorkflowStartResponse;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

@Path("/v3")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkflowResource {
    private final TemporalService temporalService;

    @Inject
    public WorkflowResource(TemporalService temporalService) {
        this.temporalService = temporalService;
    }

    @POST
    @Timed
    @Path("/workflow/start")
    @ExceptionMetered
    public Response startWorkflow(@Valid WorkflowStartRequest workflowStartRequest) {
        validateCallbackUrl(workflowStartRequest.getCallbackUrl());
        WorkflowStartResponse startResponse = temporalService.startWorkflow(workflowStartRequest);
        return Response.accepted(startResponse).build();
    }

    @PUT
    @Timed
    @Path("/workflow/resume/{workflowId}")
    @ExceptionMetered
    public Response resumeWorkflow(@Valid WorkflowResumeRequest workflowResumeRequest, @NotEmpty @PathParam("workflowId") String workflowId) {
        workflowResumeRequest.setWorkflowId(workflowId);
        WorkflowResumeResponse resumeResponse = temporalService.resumeWorkflow(workflowResumeRequest);
        return Response.accepted(resumeResponse).build();
    }

    @DELETE
    @Timed
    @Path("/workflow/terminate/{workflowId}")
    @ExceptionMetered
    public Response terminateWorkflow(@Valid WorkflowTerminateRequest workflowTerminateRequest, @NotEmpty @PathParam("workflowId") String workflowId) {
        if (workflowTerminateRequest == null) {
            workflowTerminateRequest = new WorkflowTerminateRequest("fallback: Workflow Completed");
        }
        workflowTerminateRequest.setWorkflowId(workflowId);
        temporalService.terminateWorkflow(workflowTerminateRequest);
        return Response.ok().status(200).build();
    }

    @GET
    @Timed
    @Path("/workflow/{workflowId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ExceptionMetered
    public WorkflowState getWorkflowState(@PathParam("workflowId") String workflowId) {
        return temporalService.getWorkflowState(workflowId);
    }

    @POST
    @Timed
    @Path("/workflow/{workflowId}/disconnected-node/execute")
    @ExceptionMetered
    public WorkflowUtilityResponse executeDisconnectedNode(@Valid WorkflowUtilityRequest workflowUtilityRequest, @PathParam("workflowId") String workflowId) {
        workflowUtilityRequest.setWorkflowId(workflowId);
        return temporalService.executeDisconnectedNode(workflowUtilityRequest);
    }

    /**
     * Validates the callbackUrl if provided.
     * Rules: null/blank is accepted (no callback); must be valid URI with http/https scheme; must have a host.
     *
     * @param callbackUrl the callback URL to validate (nullable)
     * @throws ApiException with HTTP 400 if the URL is invalid
     */
    private void validateCallbackUrl(String callbackUrl) {
        if (StringUtils.isBlank(callbackUrl)) {
            return; // null/blank is acceptable
        }
        try {
            URI uri = URI.create(callbackUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_CALLBACK_URL: scheme must be http or https");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_CALLBACK_URL: host is required");
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_CALLBACK_URL: " + e.getMessage());
        }
    }
}
