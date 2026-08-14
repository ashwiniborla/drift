package com.flipkart.drift.worker.service;

import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.WorkflowContextHBDao;
import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.flipkart.drift.worker.model.workflow.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowContextHBServiceTest {

    @Mock
    private WorkflowContextHBDao workflowContextHBDao;

    @InjectMocks
    private WorkflowContextHBService service;

    private WorkflowContext context(String workflowId) {
        return WorkflowContext.builder().workflowId(workflowId).build();
    }

    // createEntity

    @Test
    void createEntity_success() throws IOException {
        service.createEntity(context("wf-1"), Collections.emptyMap());
        // no exception = success
    }

    @Test
    void createEntity_ioExceptionWithCause_usesCauseMessage() throws IOException {
        IOException root = new IOException("hbase connection refused");
        IOException wrapper = new IOException("upsert failed", root);
        when(workflowContextHBDao.upsert(any(WorkflowContextHB.class), eq(ConnectionType.HOT))).thenThrow(wrapper);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createEntity(context("wf-1"), Collections.emptyMap()));

        assertEquals("hbase connection refused", ex.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void createEntity_ioExceptionWithoutCause_usesExceptionMessage() throws IOException {
        IOException bare = new IOException("table not found");
        when(workflowContextHBDao.upsert(any(WorkflowContextHB.class), eq(ConnectionType.HOT))).thenThrow(bare);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createEntity(context("wf-1"), Collections.emptyMap()));

        assertEquals("table not found", ex.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    // updateEntity

    @Test
    void updateEntity_success() throws IOException {
        service.updateEntity(context("wf-2"), Collections.emptyMap());
    }

    @Test
    void updateEntity_ioExceptionWithCause_usesCauseMessage() throws IOException {
        IOException root = new IOException("zookeeper timeout");
        IOException wrapper = new IOException("update failed", root);
        when(workflowContextHBDao.update(any(WorkflowContextHB.class), any(), eq(ConnectionType.HOT)))
                .thenThrow(wrapper);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.updateEntity(context("wf-2"), Collections.emptyMap()));

        assertEquals("zookeeper timeout", ex.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void updateEntity_ioExceptionWithoutCause_usesExceptionMessage() throws IOException {
        IOException bare = new IOException("row not found");
        when(workflowContextHBDao.update(any(WorkflowContextHB.class), any(), eq(ConnectionType.HOT)))
                .thenThrow(bare);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.updateEntity(context("wf-2"), Collections.emptyMap()));

        assertEquals("row not found", ex.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    // getEntityById

    @Test
    void getEntityById_success() throws IOException {
        WorkflowContextHB entity = new WorkflowContextHB();
        entity.setWorkflowId("wf-3");
        when(workflowContextHBDao.get(eq("wf-3"), eq(ConnectionType.HOT))).thenReturn(entity);

        WorkflowContextHB result = service.getEntityById("wf-3", Collections.emptyMap());

        assertNotNull(result);
        assertEquals("wf-3", result.getWorkflowId());
    }

    @Test
    void getEntityById_ioExceptionWithCause_usesCauseMessage() throws IOException {
        IOException root = new IOException("region server unavailable");
        IOException wrapper = new IOException("get failed", root);
        when(workflowContextHBDao.get(eq("wf-3"), eq(ConnectionType.HOT))).thenThrow(wrapper);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getEntityById("wf-3", Collections.emptyMap()));

        assertEquals("region server unavailable", ex.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void getEntityById_ioExceptionWithoutCause_usesExceptionMessage() throws IOException {
        IOException bare = new IOException("scan failed");
        when(workflowContextHBDao.get(eq("wf-3"), eq(ConnectionType.HOT))).thenThrow(bare);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getEntityById("wf-3", Collections.emptyMap()));

        assertEquals("scan failed", ex.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, ex.getStatus());
    }
}
