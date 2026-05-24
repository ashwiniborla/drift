package com.flipkart.drift.worker.task;

import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.persistence.cache.WorkflowCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationTaskTest {

    @Mock
    private NodeDefinitionCache nodeDefinitionCache;

    @Mock
    private WorkflowCache workflowCache;

    private CacheInvalidationTask task;
    private StringWriter stringWriter;
    private PrintWriter output;

    @BeforeEach
    void setUp() {
        task = new CacheInvalidationTask(nodeDefinitionCache, workflowCache);
        stringWriter = new StringWriter();
        output = new PrintWriter(stringWriter);
    }

    private Map<String, List<String>> params(String type, String key) {
        Map<String, List<String>> map = new HashMap<>();
        if (type != null) {
            map.put("type", Collections.singletonList(type));
        }
        if (key != null) {
            map.put("key", Collections.singletonList(key));
        }
        return map;
    }

    @Test
    void testExecute_nodeType_invalidatesNodeCache() throws Exception {
        task.execute(params("NODE", "my-node-key"), output);

        verify(nodeDefinitionCache, times(1)).invalidate("my-node-key");
        verify(workflowCache, never()).invalidate(anyString());
        assertTrue(stringWriter.toString().contains("OK: invalidated NODE key=my-node-key"),
                "Output should confirm NODE invalidation");
    }

    @Test
    void testExecute_workflowType_invalidatesWorkflowCache() throws Exception {
        task.execute(params("WORKFLOW", "my-wf-key"), output);

        verify(workflowCache, times(1)).invalidate("my-wf-key");
        verify(nodeDefinitionCache, never()).invalidate(anyString());
        assertTrue(stringWriter.toString().contains("OK: invalidated WORKFLOW key=my-wf-key"),
                "Output should confirm WORKFLOW invalidation");
    }

    @Test
    void testExecute_missingTypeParam_returnsError() throws Exception {
        Map<String, List<String>> noType = new HashMap<>();
        noType.put("key", Collections.singletonList("some-key"));

        task.execute(noType, output);

        assertTrue(stringWriter.toString().contains("ERROR"),
                "Missing type should produce an error message");
        verify(nodeDefinitionCache, never()).invalidate(anyString());
        verify(workflowCache, never()).invalidate(anyString());
    }

    @Test
    void testExecute_missingKeyParam_returnsError() throws Exception {
        Map<String, List<String>> noKey = new HashMap<>();
        noKey.put("type", Collections.singletonList("NODE"));

        task.execute(noKey, output);

        assertTrue(stringWriter.toString().contains("ERROR"),
                "Missing key should produce an error message");
        verify(nodeDefinitionCache, never()).invalidate(anyString());
    }

    @Test
    void testExecute_unknownType_returnsError() throws Exception {
        task.execute(params("INVALID", "some-key"), output);

        assertTrue(stringWriter.toString().contains("ERROR: unknown type"),
                "Unknown type should produce an 'unknown type' error");
        verify(nodeDefinitionCache, never()).invalidate(anyString());
        verify(workflowCache, never()).invalidate(anyString());
    }

    @Test
    void testExecute_nodeInvalidationThrows_returnsError() throws Exception {
        doThrow(new RuntimeException("HBase error")).when(nodeDefinitionCache).invalidate("bad-key");

        task.execute(params("NODE", "bad-key"), output);

        assertTrue(stringWriter.toString().contains("ERROR"),
                "Exception during invalidation should produce an error message");
    }

    @Test
    void testTaskName_isCacheInvalidate() {
        assertEquals("cache-invalidate", task.getName(),
                "Task name must be 'cache-invalidate' so Dropwizard registers it at /tasks/cache-invalidate");
    }
}
