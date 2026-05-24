package com.flipkart.drift.api.service.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.api.client.WorkerCacheInvalidationClient;
import com.flipkart.drift.api.service.utils.WorkflowGraphNode;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.Version;
import com.flipkart.drift.commons.model.node.BranchNode;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.WorkflowDefinitionDao;
import com.flipkart.drift.persistence.entity.WorkflowHB;
import com.google.inject.Inject;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Font;
import guru.nidi.graphviz.attribute.RankDir;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizJdkEngine;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Node;
import lombok.extern.slf4j.Slf4j;

import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static guru.nidi.graphviz.model.Link.to;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.flipkart.drift.api.client.WorkerCacheInvalidationClient.CacheType;
import static com.flipkart.drift.commons.utils.Utility.*;

@Slf4j
public class WorkflowDefinitionService {

    public static final String WORKFLOW_EVENT_ID = "WORKFLOW";
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final NodeDefinitionService nodeDefinitionService;
    private final WorkerCacheInvalidationClient workerCacheInvalidationClient;

    @Inject
    public WorkflowDefinitionService(WorkflowDefinitionDao workflowDefinitionDao, ObjectMapper objectMapper,
                                     NodeDefinitionService nodeDefinitionService,
                                     WorkerCacheInvalidationClient workerCacheInvalidationClient) {
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.nodeDefinitionService = nodeDefinitionService;
        this.workerCacheInvalidationClient = workerCacheInvalidationClient;
    }

    public Workflow addWorkflow(Workflow workflowData) {
        workflowData.validateWFFields();
        String workflowKey = generateRowKey(workflowData.getId(), Version.SNAPSHOT);

        checkWorkflowExistence(workflowKey);
        createWorkflow(workflowKey, workflowData);

        return workflowData;
    }

    public Workflow updateWorkflow(Workflow workflowData) throws JsonProcessingException {
        String workflowKey = generateRowKey(workflowData.getId(), Version.SNAPSHOT);

        WorkflowHB existingWorkflowHB = getWorkflowHB(workflowKey);
        Workflow existingWorkflow = existingWorkflowHB.getWorkflowData();

        // Merge fields
        if (workflowData.getComment() != null) {
            existingWorkflow.setComment(workflowData.getComment());
        }
        if (workflowData.getStartNode() != null) {
            existingWorkflow.setStartNode(workflowData.getStartNode());
        }
        if (workflowData.getVersion() != null) {
            existingWorkflow.setVersion(workflowData.getVersion());
        }
        if (workflowData.getDefaultFailureNode() != null) {
            existingWorkflow.setDefaultFailureNode(workflowData.getDefaultFailureNode());
        }
        if (workflowData.getPostWorkflowCompletionNodes() != null) {
            existingWorkflow.setPostWorkflowCompletionNodes(workflowData.getPostWorkflowCompletionNodes());
        }

        // Merge state map data individually
        if (workflowData.getStates() != null) {
            if (existingWorkflow.getStates() == null) {
                existingWorkflow.setStates(workflowData.getStates());
            } else {
                workflowData.getStates().forEach((key, value) -> existingWorkflow.getStates().put(key, value));
            }
        }

        updateWorkflowInHBase(workflowKey, existingWorkflow);
        return existingWorkflow;
    }

    public Workflow getWorkflowById(String id, String version) {
        String workflowKey = generateRowKey(id, parseVersion(version));
        return getWorkflowFromWorkflowHB(workflowKey);
    }

    public Workflow getWorkflowById(String id, String version, boolean enrichNodeDefinition) {
        Workflow workflow = getWorkflowById(id, version);
        if (enrichNodeDefinition && workflow.getStates() != null) {
            workflow.getStates().forEach((stateId, state) -> {
                NodeDefinition nodeDefinition = nodeDefinitionService.getNodeById(state.getResourceId(), state.getResourceVersion());
                state.setNodeDefinition(nodeDefinition);
            });
        }
        return workflow;
    }

    public File getWorkflowDiagram(String id, String version, String mode) {
        Graphviz.useEngine(new GraphvizJdkEngine());
        String workflowKey = generateRowKey(id, parseVersion(version));
        Workflow workflow = getWorkflowFromWorkflowHB(workflowKey);
        Map<String, WorkflowGraphNode> workflowGraph = new HashMap<>();
        Map<String, Node> graphNodes = new HashMap<>();
        String workflowName = workflow.getId().toUpperCase();

        // Add start node
        WorkflowGraphNode startWorkflowNode = new WorkflowGraphNode(
                workflowName,
                NodeType.PROCESSOR,
                workflow.getStartNode(),
                getBranchChoices(null)
        );
        workflowGraph.put(startWorkflowNode.getName(), startWorkflowNode);

        // Populate workflowGraph
        if (workflow.getStates() != null) {
            workflow.getStates().forEach((stateId, state) -> {
                NodeDefinition nodeDef = nodeDefinitionService.getNodeById(state.getResourceId(), state.getResourceVersion());
                WorkflowGraphNode workflowGraphNode = new WorkflowGraphNode(
                        state.getInstanceName(),
                        nodeDef.getType(),
                        state.getNextNode(),
                        getBranchChoices(nodeDef)
                );
                workflowGraph.put(state.getInstanceName(), workflowGraphNode);
            });
        }

        // Create graph reference
        Graph baseGraph = graph("Workflow").directed().graphAttr().with(RankDir.TOP_TO_BOTTOM);
        if (Objects.equals(mode, "dark")) {
            baseGraph = baseGraph
                    .graphAttr().with(
                            RankDir.TOP_TO_BOTTOM,
                            Color.rgb("0E0E0E").background(),
                            Color.rgb("FFFFFF").font(),
                            Font.name("Consolas")
                    )
                    .linkAttr().with(
                            Color.rgb("FFFFFF")
                    );
        }
        AtomicReference<Graph> graphRef = new AtomicReference<>(baseGraph);

        // Create graph nodes
        workflowGraph.forEach((stateId, nodeData) -> {
            Node graphNode = node(nodeData.getName())
                    .with(Style.FILLED)
                    .with(nodeData.getShape())
                    .with(nodeData.getColor());
            graphNodes.put(nodeData.getName(), graphNode);
            graphRef.set(graphRef.get().with(graphNode));
        });

        // Add edges
        workflowGraph.forEach((stateId, nodeData) -> {
            Node current = graphNodes.get(nodeData.getName());

            if (nodeData.getType() == NodeType.BRANCH && nodeData.getChoices() != null) {
                Node[] choiceNodes = nodeData.getChoices().stream()
                        .filter(graphNodes::containsKey)
                        .map(graphNodes::get)
                        .toArray(Node[]::new);

                if (choiceNodes.length > 0) {
                    Node linkedNode = current;
                    for (Node choiceNode : choiceNodes) {
                        linkedNode = linkedNode.link(to(choiceNode));
                    }
                    graphRef.set(graphRef.get().with(linkedNode));
                }
            } else if (nodeData.getNextNode() != null && graphNodes.containsKey(nodeData.getNextNode())) {
                graphRef.set(graphRef.get().with(current.link(to(graphNodes.get(nodeData.getNextNode())))));
            }
        });

        // Render PNG
        try {
            // Create secure temporary file to prevent path traversal attacks
            File workflowDiagram = File.createTempFile("workflow-" + System.currentTimeMillis(), ".png");
            Graphviz.fromGraph(graphRef.get()).render(Format.PNG).toFile(workflowDiagram);
            workflowDiagram.deleteOnExit(); // Clean up temporary file
            return workflowDiagram;
        } catch (IOException e) {
            log.error("Error while generating workflow diagram", e);
            throw new ApiException("Error while generating workflow diagram", e);
        }
    }

    private List<String> getBranchChoices(NodeDefinition nodeDefinition) {
        List<String> branchChoices = new ArrayList<>();
        if (nodeDefinition != null && nodeDefinition.getType() == NodeType.BRANCH) {
            ((BranchNode) nodeDefinition).getChoices().forEach(choice -> {
                branchChoices.add(choice.getNextNode());
            });
        }
        return branchChoices;
    }

    // PROBE::redis-removal-api-services::ENTRY
    public void publishWorkflow(String id) {
        try {
            String snapshotKey = generateRowKey(id, Version.SNAPSHOT);
            WorkflowHB snapshotWorkflowHB = getWorkflowHB(snapshotKey);
            Workflow workflow = snapshotWorkflowHB.getWorkflowData();

            String latestKey = generateRowKey(id, Version.LATEST);
            WorkflowHB latestWorkflowHB = workflowDefinitionDao.get(latestKey, ConnectionType.HOT);
            Integer version;

            if (latestWorkflowHB == null) {
                version = 1;
                workflow.setVersion(String.valueOf(version));

                createWorkflow(latestKey, workflow); //ABC_LATEST->Data of ABC_SNAPSHOT/ABC_1 with version 1

                String versionKey = generateRowKey(id, version);
                createWorkflow(versionKey, workflow); // ABC_1
                workerCacheInvalidationClient.invalidate(CacheType.WORKFLOW, versionKey);
                workerCacheInvalidationClient.invalidate(CacheType.WORKFLOW, latestKey);
                // PROBE::redis-removal-api-services::EXIT
                return;
            }
            Workflow latestWorkflow = latestWorkflowHB.getWorkflowData();
            version = StringToIntegerVersionParser(latestWorkflow.getVersion());
            version++;
            workflow.setVersion(String.valueOf(version));

            String versionKey = generateRowKey(id, version);
            createWorkflow(versionKey, workflow); // ABC_2 abc_3

            updateWorkflowInHBase(latestKey, workflow); //ABC_LATEST->Data of ABC_2 abc3
            workerCacheInvalidationClient.invalidate(CacheType.WORKFLOW, versionKey);
            workerCacheInvalidationClient.invalidate(CacheType.WORKFLOW, latestKey);

        } catch (Exception e) {
            throw new ApiException("Error while publishing workflow in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void checkWorkflowExistence(String workflowKey) {
        try {
            if (workflowDefinitionDao.get(workflowKey, ConnectionType.HOT) != null) {
                throw new ApiException(Response.Status.BAD_REQUEST, "Workflow with id already exists");
            }
        } catch (IOException e) {
            throw new ApiException("Error while checking workflow existence in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void createWorkflow(String workflowKey, Workflow workflowData) {
        try {
            WorkflowHB workflowHB = new WorkflowHB();
            workflowHB.setWorkflowKey(workflowKey);
            workflowHB.setWorkflowData(workflowData);
            workflowDefinitionDao.create(workflowHB, ConnectionType.HOT);
        } catch (IOException e) {
            throw new ApiException("Error while creating workflow in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void updateWorkflowInHBase(String workflowKey, Workflow workflowData) {
        try {
            WorkflowHB workflowHB = new WorkflowHB();
            workflowHB.setWorkflowKey(workflowKey);
            workflowHB.setWorkflowData(workflowData);
            workflowDefinitionDao.update(workflowHB, workflowKey, ConnectionType.HOT);
        } catch (IOException e) {
            throw new ApiException("Error while updating workflow in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private Workflow getWorkflowFromWorkflowHB(String workflowKey) {
        try {
            WorkflowHB workflowHB = workflowDefinitionDao.get(workflowKey, ConnectionType.HOT);
            if (workflowHB == null) {
                throw new ApiException(Response.Status.BAD_REQUEST, "Workflow not found");
            }
            return workflowHB.getWorkflowData();
        } catch (IOException e) {
            throw new ApiException("Error while fetching workflow from WorkflowHB in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    // PROBE::redis-removal-api-services::ENTRY
    public void markActive(String id, Integer versionId) {
        String versionKey = generateRowKey(id, versionId);
        WorkflowHB versionWorkflowHB = getWorkflowHB(versionKey);

        Workflow workflow = versionWorkflowHB.getWorkflowData();

        String activeKey = generateRowKey(id, Version.ACTIVE);
        createWorkflow(activeKey, workflow);
        workerCacheInvalidationClient.invalidate(CacheType.WORKFLOW, activeKey);
        // PROBE::redis-removal-api-services::EXIT
    }

    private WorkflowHB getWorkflowHB(String workflowKey) {
        try {
            WorkflowHB workflowHB = workflowDefinitionDao.get(workflowKey, ConnectionType.HOT);
            if (workflowHB == null) {
                throw new ApiException(Response.Status.BAD_REQUEST, "Workflow not found");
            }
            return workflowHB;
        } catch (IOException e) {
            throw new ApiException("Error while fetching workflow from WorkflowHB in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }
}
