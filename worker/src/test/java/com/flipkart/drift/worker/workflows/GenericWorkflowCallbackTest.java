package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.commons.model.node.SuccessNode;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.sdk.model.client.IssueDetail;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.worker.activities.CallbackActivity;
import com.flipkart.drift.worker.activities.FetchWorkflowActivity;
import com.flipkart.drift.worker.activities.SuccessNodeNodeActivity;
import com.flipkart.drift.worker.activities.WorkflowContextManagerActivity;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.model.activity.ActivityThinRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.worker.model.callback.CallbackPayload;
import com.flipkart.drift.workflows.GenericWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests for callback invocation in GenericWorkflowImpl.
 *
 * Uses TestWorkflowEnvironment (in-process Temporal server) so that
 * Workflow.getLogger() and Workflow.newActivityStub() work correctly inside
 * GenericWorkflowImpl and WorkflowNodeExecutor.
 *
 * Activities are implemented as concrete stubs (not Mockito mocks) because
 * Temporal's activity registration rejects classes that carry @ActivityMethod
 * annotations inherited from an interface.
 */
class GenericWorkflowCallbackTest {

    private static final String TASK_QUEUE = "test-callback-tq";

    private TestWorkflowEnvironment testEnv;
    private CallbackStub callbackStub;
    private FetchWorkflowStub fetchWorkflowStub;
    private WorkflowContextStub contextStub;
    private SuccessNodeStub successNodeStub;

    // ---------------------------------------------------------------------------
    // Concrete activity stubs (state is held in instance fields on the stub,
    // passed by reference into the Temporal worker so we can assert on them).
    // ---------------------------------------------------------------------------

    static class CallbackStub implements CallbackActivity {
        final List<CallbackPayload> captured = new ArrayList<>();

        @Override
        public void sendCallback(String callbackUrl, CallbackPayload payload) {
            captured.add(payload);
        }
    }

    static class FetchWorkflowStub implements FetchWorkflowActivity {
        volatile Workflow workflow;

        @Override
        public Workflow fetchWorkflow(String workflowId, String version, String tenant) {
            return workflow;
        }

        @Override
        public Workflow fetchWorkflowBasedOnRequest(WorkflowStartRequest req) {
            return workflow;
        }

        @Override
        public Workflow fetchWorkflowBasedOnIssueId(String issueId, String tenant) {
            return workflow;
        }

        @Override
        public com.flipkart.drift.commons.model.node.WorkflowNode fetchWorkflowNode(
                String issueId, String nodeName, String tenant) {
            return null;
        }
    }

    static class WorkflowContextStub implements WorkflowContextManagerActivity {
        final List<WorkflowStartRequest> persisted = new ArrayList<>();

        @Override
        public void persistWorkflowState(WorkflowStartRequest req, String workflowId) {
            persisted.add(req);
        }

        @Override
        public void resumeWorkflowState(WorkflowResumeRequest req, String currentNodeRef) { }

        @Override
        public void disconnectedNodeState(WorkflowUtilityRequest req, String workflowId) { }
    }

    static class SuccessNodeStub implements SuccessNodeNodeActivity {
        volatile WorkflowStatus statusToReturn = WorkflowStatus.COMPLETED;

        @Override
        public ActivityResponse executeNode(ActivityRequest<com.flipkart.drift.commons.model.node.SuccessNode> req) {
            return new ActivityResponse();
        }

        @Override
        public ActivityThinResponse execute(ActivityThinRequest<com.flipkart.drift.commons.model.node.SuccessNode> req) {
            return ActivityThinResponse.builder()
                    .workflowStatus(statusToReturn)
                    .build();
        }

        @Override
        public ActivityResponse executeWithFatResponse(ActivityThinRequest<com.flipkart.drift.commons.model.node.SuccessNode> req) {
            return new ActivityResponse();
        }
    }

    // ---------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);

        worker.registerWorkflowImplementationTypes(GenericWorkflowImpl.class);

        callbackStub = new CallbackStub();
        fetchWorkflowStub = new FetchWorkflowStub();
        contextStub = new WorkflowContextStub();
        successNodeStub = new SuccessNodeStub();

        worker.registerActivitiesImplementations(
                callbackStub,
                fetchWorkflowStub,
                contextStub,
                successNodeStub
        );

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    private WorkflowStartRequest buildRequest(String callbackUrl) {
        WorkflowStartRequest req = new WorkflowStartRequest();
        req.setWorkflowId("wf-001");
        req.setCallbackUrl(callbackUrl);
        IssueDetail issueDetail = new IssueDetail();
        issueDetail.setIssueId("issue-001");
        req.setIssueDetail(issueDetail);
        req.setThreadContext(new HashMap<>());
        return req;
    }

    private Workflow buildOneNodeWorkflow(WorkflowStatus terminalStatus) {
        SuccessNode successNode = new SuccessNode();
        successNode.setId("n1");
        successNode.setName("success");

        WorkflowNode wfNode = new WorkflowNode();
        wfNode.setInstanceName("start-node");
        wfNode.setNodeDefinition(successNode);
        wfNode.setEnd(true);

        Map<String, WorkflowNode> states = new HashMap<>();
        states.put("start-node", wfNode);

        Workflow wf = new Workflow();
        wf.setId("test-wf");
        wf.setStartNode("start-node");
        wf.setStates(states);
        return wf;
    }

    // -----------------------------------------------------------------------
    // Test 1: callbackUrl is stored in WorkflowState during initializeWorkflow
    // -----------------------------------------------------------------------
    @Test
    void testInitializeWorkflow_storesCallbackUrlInPersistedRequest() throws InterruptedException {
        // Workflow will fail because fetch returns null — but initializeWorkflow runs first
        fetchWorkflowStub.workflow = null;
        String callbackUrl = "http://callback.example.com/hook";

        WorkflowClient client = testEnv.getWorkflowClient();
        GenericWorkflow wf = client.newWorkflowStub(GenericWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("wf-init-test")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        // Non-blocking start; workflow will fail asynchronously
        WorkflowClient.start(wf::startWorkflow, buildRequest(callbackUrl));

        // Wait for workflow to reach persistWorkflowState (or fail)
        testEnv.sleep(java.time.Duration.ofMillis(500));

        // Verify that initializeWorkflow ran and passed callbackUrl to persistWorkflowState
        assertFalse(contextStub.persisted.isEmpty(),
                "persistWorkflowState should have been called during initializeWorkflow");
        assertEquals(callbackUrl, contextStub.persisted.get(0).getCallbackUrl(),
                "The persisted WorkflowStartRequest must carry the callbackUrl set by initializeWorkflow");
    }

    // -----------------------------------------------------------------------
    // Test 2: COMPLETED workflow with callbackUrl → sendCallback invoked
    // -----------------------------------------------------------------------
    @Test
    void testCompletedWorkflow_withCallbackUrl_invokesCallback() {
        fetchWorkflowStub.workflow = buildOneNodeWorkflow(WorkflowStatus.COMPLETED);
        successNodeStub.statusToReturn = WorkflowStatus.COMPLETED;
        String callbackUrl = "http://callback.example.com/done";

        WorkflowClient client = testEnv.getWorkflowClient();
        GenericWorkflow wf = client.newWorkflowStub(GenericWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("wf-completed-cb")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        // Start and wait synchronously (blocking start)
        wf.startWorkflow(buildRequest(callbackUrl));

        assertEquals(1, callbackStub.captured.size(),
                "sendCallback should have been called exactly once for COMPLETED workflow");
        CallbackPayload payload = callbackStub.captured.get(0);
        // workflowId in payload comes from workflowStartRequest.getWorkflowId() (passed down via executeWorkflowNodes)
        assertEquals("wf-001", payload.getWorkflowId(),
                "Callback payload workflowId must match the workflowStartRequest.getWorkflowId()");
        assertEquals(WorkflowStatus.COMPLETED, payload.getWorkflowStatus());
    }

    // -----------------------------------------------------------------------
    // Test 3: COMPLETED workflow with no callbackUrl → sendCallback NOT invoked
    // -----------------------------------------------------------------------
    @Test
    void testCompletedWorkflow_withoutCallbackUrl_doesNotInvokeCallback() {
        fetchWorkflowStub.workflow = buildOneNodeWorkflow(WorkflowStatus.COMPLETED);
        successNodeStub.statusToReturn = WorkflowStatus.COMPLETED;

        WorkflowClient client = testEnv.getWorkflowClient();
        GenericWorkflow wf = client.newWorkflowStub(GenericWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("wf-no-cb")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        wf.startWorkflow(buildRequest(null));

        assertTrue(callbackStub.captured.isEmpty(),
                "sendCallback must NOT be called when callbackUrl is null");
    }

    // -----------------------------------------------------------------------
    // Test 4: ASYNC_COMPLETE workflow with callbackUrl → sendCallback invoked
    // -----------------------------------------------------------------------
    @Test
    void testAsyncCompleteWorkflow_withCallbackUrl_invokesCallback() {
        fetchWorkflowStub.workflow = buildOneNodeWorkflow(WorkflowStatus.ASYNC_COMPLETE);
        successNodeStub.statusToReturn = WorkflowStatus.ASYNC_COMPLETE;
        String callbackUrl = "http://callback.example.com/async";

        WorkflowClient client = testEnv.getWorkflowClient();
        GenericWorkflow wf = client.newWorkflowStub(GenericWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("wf-async-cb")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        wf.startWorkflow(buildRequest(callbackUrl));

        assertEquals(1, callbackStub.captured.size(),
                "sendCallback should be called once for ASYNC_COMPLETE terminal state");
        assertEquals(WorkflowStatus.ASYNC_COMPLETE, callbackStub.captured.get(0).getWorkflowStatus());
    }

    // -----------------------------------------------------------------------
    // Test 5: DELEGATED workflow with callbackUrl → sendCallback invoked
    // -----------------------------------------------------------------------
    @Test
    void testDelegatedWorkflow_withCallbackUrl_invokesCallback() {
        fetchWorkflowStub.workflow = buildOneNodeWorkflow(WorkflowStatus.DELEGATED);
        successNodeStub.statusToReturn = WorkflowStatus.DELEGATED;
        String callbackUrl = "http://callback.example.com/delegated";

        WorkflowClient client = testEnv.getWorkflowClient();
        GenericWorkflow wf = client.newWorkflowStub(GenericWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("wf-delegated-cb")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        wf.startWorkflow(buildRequest(callbackUrl));

        assertEquals(1, callbackStub.captured.size(),
                "sendCallback should be called once for DELEGATED terminal state");
        assertEquals(WorkflowStatus.DELEGATED, callbackStub.captured.get(0).getWorkflowStatus());
    }
}
