package com.flipkart.drift.api.service.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.SuccessNode;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.NodeDefinitionDao;
import com.flipkart.drift.persistence.entity.NodeHB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeDefinitionServiceTest {

    @Mock
    private NodeDefinitionDao nodeDefinitionDao;

    @Mock
    private JedisSentinelPool jedisSentinelPool;

    @Mock
    private Jedis jedis;

    @Mock
    private ObjectMapper objectMapper;

    private NodeDefinitionService service;

    private static final String NODE_ID = "testNode";
    private static final String SNAPSHOT_KEY = NODE_ID + "_SNAPSHOT";
    private static final String LATEST_KEY = NODE_ID + "_LATEST";

    @BeforeEach
    void setUp() {
        service = new NodeDefinitionService(nodeDefinitionDao, objectMapper, jedisSentinelPool);
        lenient().when(jedisSentinelPool.getResource()).thenReturn(jedis);
    }

    private NodeHB buildNodeHB(String key, String version) {
        SuccessNode node = new SuccessNode();
        node.setId(NODE_ID);
        node.setName("testNodeName");
        if (version != null) {
            node.setVersion(version);
        }
        NodeHB hb = new NodeHB();
        hb.setNodeKey(key);
        hb.setNodeData(node);
        return hb;
    }

    @Test
    void publishNode_firstPublish_returnsNodeWithVersionOne() throws IOException {
        NodeHB snapshotHB = buildNodeHB(SNAPSHOT_KEY, null);
        when(nodeDefinitionDao.get(eq(SNAPSHOT_KEY), eq(ConnectionType.HOT))).thenReturn(snapshotHB);
        when(nodeDefinitionDao.get(eq(LATEST_KEY), eq(ConnectionType.HOT))).thenReturn(null);

        NodeDefinition result = service.publishNode(NODE_ID);

        assertNotNull(result);
        assertEquals("1", result.getVersion());
    }

    @Test
    void publishNode_subsequentPublish_returnsNodeWithIncrementedVersion() throws IOException {
        NodeHB snapshotHB = buildNodeHB(SNAPSHOT_KEY, null);
        NodeHB latestHB = buildNodeHB(LATEST_KEY, "3");
        when(nodeDefinitionDao.get(eq(SNAPSHOT_KEY), eq(ConnectionType.HOT))).thenReturn(snapshotHB);
        when(nodeDefinitionDao.get(eq(LATEST_KEY), eq(ConnectionType.HOT))).thenReturn(latestHB);

        NodeDefinition result = service.publishNode(NODE_ID);

        assertNotNull(result);
        assertEquals("4", result.getVersion());
    }

    @Test
    void publishNode_exceptionFromDao_throwsApiException() throws IOException {
        when(nodeDefinitionDao.get(eq(SNAPSHOT_KEY), eq(ConnectionType.HOT))).thenThrow(new IOException("hbase read failed"));

        ApiException ex = assertThrows(ApiException.class, () -> service.publishNode(NODE_ID));

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }
}
