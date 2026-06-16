package com.flipkart.drift.api.service.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.commons.model.node.*;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.WorkflowDefinitionDao;
import com.flipkart.drift.persistence.entity.WorkflowHB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisSentinelPool;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

    @Mock private WorkflowDefinitionDao workflowDefinitionDao;
    @Mock private NodeDefinitionService nodeDefinitionService;
    @Mock private JedisSentinelPool jedisSentinelPool;

    private WorkflowDefinitionService service;

    private static final String WF_ID = "test_wf";

    @BeforeEach
    void setUp() {
        service = new WorkflowDefinitionService(workflowDefinitionDao, new ObjectMapper(),
                jedisSentinelPool, nodeDefinitionService);
    }

    private WorkflowNode node(String name) {
        WorkflowNode n = new WorkflowNode();
        n.setInstanceName(name);
        n.setResourceId("res_" + name);
        n.setResourceVersion("1");
        return n;
    }

    private Workflow wf(String startNode, String... nodeNames) {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        for (String name : nodeNames) states.put(name, node(name));
        Workflow w = new Workflow();
        w.setId(WF_ID);
        w.setStartNode(startNode);
        w.setStates(states);
        return w;
    }

    private void stubDao(Workflow w) throws IOException {
        WorkflowHB hb = new WorkflowHB();
        hb.setWorkflowKey(WF_ID + "_SNAPSHOT");
        hb.setWorkflowData(w);
        when(workflowDefinitionDao.get(anyString(), eq(ConnectionType.HOT))).thenReturn(hb);
    }

    @Test
    void t01_put_fullReplacement_deletesOmittedNode() throws Exception {
        // Existing: nodeA (start), nodeB, nodeC — payload omits nodeC → nodeC deleted
        Workflow existing = wf("nodeA", "nodeA", "nodeB", "nodeC");
        stubDao(existing);

        Workflow payload = wf("nodeA", "nodeA", "nodeB");
        Workflow result = service.updateWorkflow(payload);

        assertTrue(result.getStates().containsKey("nodeA"));
        assertTrue(result.getStates().containsKey("nodeB"));
        assertFalse(result.getStates().containsKey("nodeC"));
    }

    @Test
    void t02_put_batchDelete_omitMultipleNodes() throws Exception {
        // Payload omits nodeB and nodeC in one PUT — both deleted atomically
        Workflow existing = wf("nodeA", "nodeA", "nodeB", "nodeC");
        stubDao(existing);

        Workflow payload = wf("nodeA", "nodeA");
        Workflow result = service.updateWorkflow(payload);

        assertEquals(1, result.getStates().size());
        assertTrue(result.getStates().containsKey("nodeA"));
    }

    @Test
    void t03_put_statesNull_metadataOnlyUpdate_statesUnchanged() throws Exception {
        // Payload has no states field (null) → states untouched, only comment updated
        Workflow existing = wf("nodeA", "nodeA", "nodeB");
        existing.setComment("old comment");
        stubDao(existing);

        Workflow payload = new Workflow();
        payload.setId(WF_ID);
        payload.setComment("new comment");

        Workflow result = service.updateWorkflow(payload);

        assertEquals("new comment", result.getComment());
        assertTrue(result.getStates().containsKey("nodeA"), "states must be preserved when payload states is null");
        assertTrue(result.getStates().containsKey("nodeB"), "states must be preserved when payload states is null");
    }

    // ---- graph-integrity validation tests (PUT path) ----

    @Test
    void t04_put_danglingNextNode_rejected400() throws Exception {
        // nodeA.nextNode points to nodeB, but nodeB is omitted from payload → 400
        Workflow existing = wf("nodeA", "nodeA", "nodeB");
        stubDao(existing);

        Workflow payload = wf("nodeA", "nodeA");
        payload.getStates().get("nodeA").setNextNode("nodeB"); // dangling reference

        ApiException ex = assertThrows(ApiException.class, () -> service.updateWorkflow(payload));
        assertEquals(400, ex.getStatus().getStatusCode());
        assertTrue(ex.getMessage().contains("NEXT_NODE from=nodeA target=nodeB"));
    }

    @Test
    void t05_put_danglingDefaultFailureNode_rejected400() throws Exception {
        Workflow existing = wf("nodeA", "nodeA", "nodeB");
        stubDao(existing);

        Workflow payload = wf("nodeA", "nodeA"); // nodeB omitted
        payload.setDefaultFailureNode("nodeB");  // still references nodeB

        ApiException ex = assertThrows(ApiException.class, () -> service.updateWorkflow(payload));
        assertEquals(400, ex.getStatus().getStatusCode());
        assertTrue(ex.getMessage().contains("DEFAULT_FAILURE target=nodeB"));
    }

    @Test
    void t06_put_danglingCompletionNode_rejected400() throws Exception {
        Workflow existing = wf("nodeA", "nodeA", "nodeB");
        stubDao(existing);

        Workflow payload = wf("nodeA", "nodeA"); // nodeB omitted
        payload.setPostWorkflowCompletionNodes(Collections.singletonList("nodeB"));

        ApiException ex = assertThrows(ApiException.class, () -> service.updateWorkflow(payload));
        assertEquals(400, ex.getStatus().getStatusCode());
        assertTrue(ex.getMessage().contains("COMPLETION target=nodeB"));
    }

    @Test
    void t07_put_startNodeNotInStates_rejected400() throws Exception {
        Workflow existing = wf("nodeA", "nodeA", "nodeB");
        stubDao(existing);

        // Payload changes startNode to a node that isn't in the submitted states
        Workflow payload = wf("nodeA", "nodeA", "nodeB");
        payload.setStartNode("nodeX"); // nodeX doesn't exist in states

        ApiException ex = assertThrows(ApiException.class, () -> service.updateWorkflow(payload));
        assertEquals(400, ex.getStatus().getStatusCode());
        assertTrue(ex.getMessage().contains("START_NODE target=nodeX"));
    }

    // ---- graph-integrity validation tests (publishWorkflow path) ----

    @Test
    void t08_publish_brokenSnapshot_rejected400() throws Exception {
        // SNAPSHOT has nodeA.nextNode pointing to nodeB, but nodeB is missing from states
        Workflow broken = wf("nodeA", "nodeA");
        broken.getStates().get("nodeA").setNextNode("nodeB"); // dangling
        stubDao(broken);

        ApiException ex = assertThrows(ApiException.class, () -> service.publishWorkflow(WF_ID));
        assertEquals(400, ex.getStatus().getStatusCode());
        assertTrue(ex.getMessage().contains("NEXT_NODE from=nodeA target=nodeB"));
    }
}
