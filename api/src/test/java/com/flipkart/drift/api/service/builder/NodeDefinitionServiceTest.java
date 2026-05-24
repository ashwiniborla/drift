package com.flipkart.drift.api.service.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.api.client.WorkerCacheInvalidationClient;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.node.HttpNode;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.NodeDefinitionDao;
import com.flipkart.drift.persistence.entity.NodeHB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeDefinitionServiceTest {

    @Mock
    private NodeDefinitionDao nodeDefinitionDao;

    @Mock
    private WorkerCacheInvalidationClient workerCacheInvalidationClient;

    private NodeDefinitionService nodeDefinitionService;

    @BeforeEach
    void setUp() {
        nodeDefinitionService = new NodeDefinitionService(nodeDefinitionDao, new ObjectMapper(), workerCacheInvalidationClient);
    }

    private NodeDefinition buildRealNodeDefinition(String id, String version) {
        HttpNode nodeDef = new HttpNode();
        nodeDef.setId(id);
        nodeDef.setName("test-node");
        nodeDef.setType(NodeType.HTTP);
        nodeDef.setVersion(version);
        return nodeDef;
    }

    @Test
    void testPublishNode_firstVersion_callsCacheInvalidationTwice() throws Exception {
        NodeDefinition nodeDef = buildRealNodeDefinition("node-1", null);

        NodeHB snapshotHB = new NodeHB();
        snapshotHB.setNodeData(nodeDef);
        snapshotHB.setNodeKey("node-1_SNAPSHOT");

        when(nodeDefinitionDao.get(contains("SNAPSHOT"), eq(ConnectionType.HOT))).thenReturn(snapshotHB);
        when(nodeDefinitionDao.get(contains("LATEST"), eq(ConnectionType.HOT))).thenReturn(null);

        nodeDefinitionService.publishNode("node-1");

        verify(workerCacheInvalidationClient, times(2))
                .invalidate(eq(WorkerCacheInvalidationClient.CacheType.NODE), anyString());
    }

    @Test
    void testPublishNode_subsequentVersion_callsCacheInvalidationTwice() throws Exception {
        NodeDefinition existingNodeDef = buildRealNodeDefinition("node-1", "1");
        NodeDefinition snapshotNodeDef = buildRealNodeDefinition("node-1", null);

        NodeHB snapshotHB = new NodeHB();
        snapshotHB.setNodeData(snapshotNodeDef);
        snapshotHB.setNodeKey("node-1_SNAPSHOT");

        NodeHB latestHB = new NodeHB();
        latestHB.setNodeData(existingNodeDef);
        latestHB.setNodeKey("node-1_LATEST");

        when(nodeDefinitionDao.get(contains("SNAPSHOT"), eq(ConnectionType.HOT))).thenReturn(snapshotHB);
        when(nodeDefinitionDao.get(contains("LATEST"), eq(ConnectionType.HOT))).thenReturn(latestHB);

        nodeDefinitionService.publishNode("node-1");

        verify(workerCacheInvalidationClient, times(2))
                .invalidate(eq(WorkerCacheInvalidationClient.CacheType.NODE), anyString());
    }

    @Test
    void testNodeDefinitionServiceConstructor_takesWorkerCacheInvalidationClient() {
        try {
            NodeDefinitionService.class.getDeclaredConstructor(
                    NodeDefinitionDao.class, ObjectMapper.class, WorkerCacheInvalidationClient.class);
        } catch (NoSuchMethodException e) {
            fail("NodeDefinitionService must have constructor(NodeDefinitionDao, ObjectMapper, WorkerCacheInvalidationClient)");
        }
    }

    @Test
    void testNoJedisFieldInNodeDefinitionService() {
        var fields = NodeDefinitionService.class.getDeclaredFields();
        for (var field : fields) {
            assertFalse(field.getType().getName().contains("jedis"),
                    "No Jedis field should be present in NodeDefinitionService");
        }
    }
}
