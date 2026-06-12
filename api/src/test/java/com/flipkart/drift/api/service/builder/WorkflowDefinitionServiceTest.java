package com.flipkart.drift.api.service.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.WorkflowDefinitionDao;
import com.flipkart.drift.persistence.entity.WorkflowHB;
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
class WorkflowDefinitionServiceTest {

    @Mock
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Mock
    private JedisSentinelPool jedisSentinelPool;

    @Mock
    private Jedis jedis;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NodeDefinitionService nodeDefinitionService;

    private WorkflowDefinitionService service;

    private static final String WORKFLOW_ID = "testWorkflow";
    private static final String SNAPSHOT_KEY = WORKFLOW_ID + "_SNAPSHOT";
    private static final String LATEST_KEY = WORKFLOW_ID + "_LATEST";

    @BeforeEach
    void setUp() {
        service = new WorkflowDefinitionService(workflowDefinitionDao, objectMapper, jedisSentinelPool, nodeDefinitionService);
        lenient().when(jedisSentinelPool.getResource()).thenReturn(jedis);
    }

    private WorkflowHB buildWorkflowHB(String key, String version) {
        Workflow workflow = new Workflow();
        workflow.setId(WORKFLOW_ID);
        workflow.setStartNode("startNode");
        if (version != null) {
            workflow.setVersion(version);
        }
        WorkflowHB hb = new WorkflowHB();
        hb.setWorkflowKey(key);
        hb.setWorkflowData(workflow);
        return hb;
    }

    @Test
    void publishWorkflow_firstPublish_returnsWorkflowWithVersionOne() throws IOException {
        WorkflowHB snapshotHB = buildWorkflowHB(SNAPSHOT_KEY, null);
        when(workflowDefinitionDao.get(eq(SNAPSHOT_KEY), eq(ConnectionType.HOT))).thenReturn(snapshotHB);
        when(workflowDefinitionDao.get(eq(LATEST_KEY), eq(ConnectionType.HOT))).thenReturn(null);

        Workflow result = service.publishWorkflow(WORKFLOW_ID);

        assertNotNull(result);
        assertEquals("1", result.getVersion());
    }

    @Test
    void publishWorkflow_subsequentPublish_returnsWorkflowWithIncrementedVersion() throws IOException {
        WorkflowHB snapshotHB = buildWorkflowHB(SNAPSHOT_KEY, null);
        WorkflowHB latestHB = buildWorkflowHB(LATEST_KEY, "5");
        when(workflowDefinitionDao.get(eq(SNAPSHOT_KEY), eq(ConnectionType.HOT))).thenReturn(snapshotHB);
        when(workflowDefinitionDao.get(eq(LATEST_KEY), eq(ConnectionType.HOT))).thenReturn(latestHB);

        Workflow result = service.publishWorkflow(WORKFLOW_ID);

        assertNotNull(result);
        assertEquals("6", result.getVersion());
    }

    @Test
    void publishWorkflow_exceptionFromDao_throwsApiException() throws IOException {
        when(workflowDefinitionDao.get(eq(SNAPSHOT_KEY), eq(ConnectionType.HOT))).thenThrow(new IOException("hbase read failed"));

        ApiException ex = assertThrows(ApiException.class, () -> service.publishWorkflow(WORKFLOW_ID));

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }
}
