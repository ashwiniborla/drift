package com.flipkart.drift.api.resources;

import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.api.service.builder.NodeDefinitionService;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/nodeDefinition")
@Produces(MediaType.APPLICATION_JSON)
@Api("/nodeDefinition")
@Slf4j
public class NodeDefinitionResource {
    private final NodeDefinitionService nodeDefinitionService;

    @Inject
    public NodeDefinitionResource(NodeDefinitionService nodeDefinitionService) {
        this.nodeDefinitionService = nodeDefinitionService;
    }

    @POST
    @Path("/")
    @Timed
    public Response addNode(@Valid @NotNull NodeDefinition wfNodeData) {
        try {
            NodeDefinition wfNodeResponse = nodeDefinitionService.addNode(wfNodeData);
            return Response.ok(wfNodeResponse).build();
        } catch (Exception e) {
            log.error("Error adding node", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/")
    @Timed
    public Response updateNode(@Valid @NotNull NodeDefinition wfNodeData) {
        try {
            NodeDefinition wfNodeResponse = nodeDefinitionService.updateNode(wfNodeData);
            return Response.ok(wfNodeResponse).build();
        } catch (Exception e) {
            log.error("Error updating node", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}")
    @Timed
    public Response getNodeById(@NotEmpty @NotNull @PathParam("id") String id, @NotEmpty @NotNull @QueryParam("version") String version) {
        try {
            NodeDefinition node = nodeDefinitionService.getNodeById(id, version);
            return Response.ok(node).build();
        } catch (Exception e) {
            log.error("Error getting node by ID", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/publishNode/")
    @Timed
    public Response publishNode(@NotEmpty @NotNull @PathParam("id") String id) {
        try {
            NodeDefinition nodeDefinition = nodeDefinitionService.publishNode(id);
            return Response.ok(nodeDefinition).build();
        } catch (Exception e) {
            log.error("Error publishing node", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}




