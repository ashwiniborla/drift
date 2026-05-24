package com.flipkart.drift.api.service.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.api.client.WorkerCacheInvalidationClient;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.NodeDefinitionDao;
import com.flipkart.drift.persistence.entity.NodeHB;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.Version;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.google.inject.Inject;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.flipkart.drift.commons.utils.Utility.*;
import static com.flipkart.drift.api.client.WorkerCacheInvalidationClient.CacheType;

public class NodeDefinitionService {
    public static final String NODE_EVENT_ID = "NODE";
    private final NodeDefinitionDao nodeDefinitionDao;
    private final WorkerCacheInvalidationClient workerCacheInvalidationClient;

    @Inject
    public NodeDefinitionService(NodeDefinitionDao nodeDefinitionDao, ObjectMapper objectMapper,
                                 WorkerCacheInvalidationClient workerCacheInvalidationClient) {
        this.nodeDefinitionDao = nodeDefinitionDao;
        this.workerCacheInvalidationClient = workerCacheInvalidationClient;
    }

    public NodeDefinition addNode(NodeDefinition wfNodeData) {
        wfNodeData.validateWFNodeFields();
        String nodeKey = generateRowKey(wfNodeData.getId(), Version.SNAPSHOT);
        checkNodeExistence(nodeKey);
        createNode(nodeKey, wfNodeData);
        return wfNodeData;
    }

    public NodeDefinition updateNode(NodeDefinition wfNodeData) throws IOException {
        String nodeKey = generateRowKey(wfNodeData.getId(), Version.SNAPSHOT);
        NodeHB existingNodeHB = getNodeHB(nodeKey);
        NodeDefinition existingNode = updateExistingNode(existingNodeHB, wfNodeData);

        updateNodeInHBase(nodeKey, existingNode);
        return wfNodeData;
    }

    public NodeDefinition getNodeById(String id, String version) {
        String nodeKey = generateRowKey(id, parseVersion(version));
        return getNodeFromNodeHB(nodeKey);
    }

    private NodeDefinition getNodeFromNodeHB(String nodeKey) {
        try {
            NodeHB nodeHB = nodeDefinitionDao.get(nodeKey, ConnectionType.HOT);
            if (nodeHB == null) {
                throw new ApiException(Response.Status.BAD_REQUEST, "Node not found");
            }
            return nodeHB.getNodeData();
        } catch (IOException e) {
            throw new ApiException("Error while fetching node from NodeHB in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    public void publishNode(String id) {
        try {
            String snapshotKey = generateRowKey(id, Version.SNAPSHOT);
            NodeHB snapshotNodeHB = getNodeHB(snapshotKey);
            NodeDefinition nodeDefinition = snapshotNodeHB.getNodeData();
            String latestKey = generateRowKey(id, Version.LATEST);
            NodeHB latestNodeHB = nodeDefinitionDao.get(latestKey, ConnectionType.HOT);
            Integer version;

            if (latestNodeHB == null) {
                version = 1;
                nodeDefinition.setVersion(String.valueOf(version));
                createNode(latestKey, nodeDefinition); //ABC_LATEST->Data of ABC_SNAPSHOT/ABC_1 with version 1

                String versionKey = generateRowKey(id, version);
                createNode(versionKey, nodeDefinition); // ABC_1

                workerCacheInvalidationClient.invalidate(CacheType.NODE, versionKey);
                workerCacheInvalidationClient.invalidate(CacheType.NODE, latestKey);
                return;
            }
            NodeDefinition latestNodeDefinition = latestNodeHB.getNodeData();
            version = StringToIntegerVersionParser(latestNodeDefinition.getVersion());
            version++;
            nodeDefinition.setVersion(String.valueOf(version));

            String versionKey = generateRowKey(id, version);
            createNode(versionKey, nodeDefinition); //ABC_2, ABC_3

            updateNodeInHBase(generateRowKey(id, Version.LATEST), nodeDefinition);//Update of latest
            workerCacheInvalidationClient.invalidate(CacheType.NODE, versionKey);
            workerCacheInvalidationClient.invalidate(CacheType.NODE, latestKey);

        } catch (Exception e) {
            throw new ApiException("Error while publishing node in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void checkNodeExistence(String nodeKey) {
        try {
            if (nodeDefinitionDao.get(nodeKey, ConnectionType.HOT) != null) {
                throw new ApiException(Response.Status.BAD_REQUEST, "Node with name already exists");
            }
        } catch (IOException e) {
            throw new ApiException("Error while checking node existence in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void createNode(String nodeKey, NodeDefinition wfNodeData) {
        try {
            NodeHB nodeHB = new NodeHB();
            nodeHB.setNodeKey(nodeKey);
            nodeHB.setNodeData(wfNodeData);
            nodeDefinitionDao.create(nodeHB, ConnectionType.HOT);
        } catch (IOException e) {
            throw new ApiException("Error while creating node in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private NodeHB getNodeHB(String nodeKey) {
        try {
            NodeHB nodeHB = nodeDefinitionDao.get(nodeKey, ConnectionType.HOT);
            if (nodeHB == null) {
                throw new ApiException(Response.Status.BAD_REQUEST, "Node not found");
            }
            return nodeHB;
        } catch (IOException e) {
            throw new ApiException("Error while fetching node from NodeHB in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    private NodeDefinition updateExistingNode(NodeHB existingNodeHB, NodeDefinition wfNodeData) throws IOException {
        NodeDefinition existingNode = existingNodeHB.getNodeData();
        if (wfNodeData.getName() != null) {
            existingNode.setName(wfNodeData.getName());
        }
        if (wfNodeData.getType() != null) {
            existingNode.setType(wfNodeData.getType());
        }
        if (wfNodeData.getParameters() != null) {
            existingNode.setParameters(wfNodeData.getParameters());
        }
        existingNode.mergeRequestToEntity(wfNodeData);
        return existingNode;
    }

    private void updateNodeInHBase(String nodeKey, NodeDefinition nodeDefinition) {
        try {
            NodeHB nodeHB = new NodeHB();
            nodeHB.setNodeKey(nodeKey);
            nodeHB.setNodeData(nodeDefinition);
            nodeDefinitionDao.update(nodeHB, nodeKey, ConnectionType.HOT);
        } catch (IOException e) {
            throw new ApiException("Error while updating node in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }
}
