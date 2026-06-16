package com.flipkart.drift.api.resources;

import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.api.service.builder.WorkflowDefinitionService;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;

@Path("/workflowDefinition")
@Produces(MediaType.APPLICATION_JSON)
@Api("/workflowDefinition")
@Slf4j
public class WorkflowDefinitionResource {
    private final WorkflowDefinitionService workflowDefinitionService;

    @Inject
    public WorkflowDefinitionResource(WorkflowDefinitionService workflowDefinitionService) {
        this.workflowDefinitionService = workflowDefinitionService;
    }

    @POST
    @Path("/")
    @Timed
    public Response addWorkflow(@Valid @NotNull Workflow workflowData) {
        try {
            Workflow workflowResponse = workflowDefinitionService.addWorkflow(workflowData);
            return Response.ok(workflowResponse).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/")
    @Timed
    public Response updateWorkflow(@Valid @NotNull Workflow workflowData) {
        try {
            Workflow workflowResponse = workflowDefinitionService.updateWorkflow(workflowData);
            return Response.ok(workflowResponse).build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}")
    @Timed
    public Response getWorkflowById(@NotEmpty @NotNull @PathParam("id") String id,@NotEmpty @NotNull @QueryParam("version") String version,
                                    @QueryParam("enrichNodeDefinition") @DefaultValue("false") boolean enrichNodeDefinition) {
        try {
            Workflow workflow = workflowDefinitionService.getWorkflowById(id, version, enrichNodeDefinition);
            return Response.ok(workflow).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/publishWorkflow/")
    @Timed
    public Response publishWorkflow(@NotEmpty @PathParam("id") String id) {
        try {
            workflowDefinitionService.publishWorkflow(id);
            return Response.ok().build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/activate")
    @Timed
    public Response markActive(@NotEmpty @NotNull @PathParam("id") String id, @NotNull @QueryParam("version") Integer versionId) {
        try {
            workflowDefinitionService.markActive(id, versionId);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/treeView/{id}")
    @Produces("image/png")
    public Response getWorkflowDiagram(@NotEmpty @NotNull @PathParam("id") String id, @NotEmpty @NotNull @QueryParam("version") String version, @DefaultValue("light") @QueryParam("mode") String mode) {
        try {
            File workflowDiagram = workflowDefinitionService.getWorkflowDiagram(id, version, mode);
            return Response.ok(workflowDiagram, "image/png")
                    .header("Content-Disposition", "inline; filename=\"" + workflowDiagram.getName() + "\"")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}




