package com.flipkart.drift.worker.service;

import com.flipkart.drift.commons.model.clientComponent.BranchComponents;
import com.flipkart.drift.commons.model.enums.WorkflowNodeType;
import com.flipkart.drift.commons.model.node.*;
import com.flipkart.drift.worker.helper.WorkflowEnrichHelper;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SubWorkflowFlattenerTest {

    private static final String TENANT = "cs";
    private static final String V = "SNAPSHOT";

    private WorkflowEnrichHelper enrichHelper;
    private SubWorkflowFlattener flattener;

    @BeforeEach
    void setUp() {
        enrichHelper = Mockito.mock(WorkflowEnrichHelper.class);
        flattener = new SubWorkflowFlattener(enrichHelper);
    }

    // ========================== Group 1: Basic Include/Exclude ==========================

    @Test
    void includeAll_inlinesAllNodes() {
        Workflow a = parentWithOneSub("SubB", true, true);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(threeNodeSub("SubB"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2", "b3", "a2");
        assertLinearChain(a, "a1", "b1", "b2", "b3", "a2");
    }

    @Test
    void excludeFirst_skipsFirstNode() {
        Workflow a = parentWithOneSub("SubB", false, true);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(threeNodeSub("SubB"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b2", "b3", "a2");
        assertLinearChain(a, "a1", "b2", "b3", "a2");
    }

    @Test
    void excludeLast_skipsLastNode() {
        Workflow a = parentWithOneSub("SubB", true, false);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(threeNodeSub("SubB"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2", "a2");
        assertLinearChain(a, "a1", "b1", "b2", "a2");
    }

    @Test
    void excludeBoth_skipsFirstAndLastNodes() {
        Workflow a = parentWithOneSub("SubB", false, false);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(threeNodeSub("SubB"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b2", "a2");
        assertLinearChain(a, "a1", "b2", "a2");
    }

    // ========================== Group 2: Small Sub Edge Cases ==========================

    @Test
    void singleNode_includeAll() {
        Workflow a = parentWithOneSub("SubB", true, true);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "b1"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "a2");
        assertLinearChain(a, "a1", "b1", "a2");
    }

    @Test
    void singleNode_excludeBoth_emptyScope_passThrough() {
        Workflow a = parentWithOneSub("SubB", false, false);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "b1"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "a2");
        assertLinearChain(a, "a1", "a2");
    }

    @Test
    void twoNodes_excludeBoth_emptyScope_passThrough() {
        Workflow a = parentWithOneSub("SubB", false, false);
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "a2");
        assertLinearChain(a, "a1", "a2");
    }

    // ========================== Group 3: Nested (Multi-Level) ==========================

    @Test
    void twoLevelNesting() {
        // A: a1 → sub_b → a2. B: b1 → sub_c → b3. C: c1 → c2.
        Workflow c = workflow("SubC", "c1", linkedMap(
                "c1", node("c1", groovyDef("c1"), "c2", false),
                "c2", node("c2", groovyDef("c2"), null, true)));
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "sub_c", false),
                "sub_c", subNode("sub_c", subWfDef("sub_c", "SubC", true, true), "b3", false),
                "b3", node("b3", groovyDef("b3"), null, true)));
        Workflow a = parentWithOneSub("SubB", true, true);

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(c);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "c1", "c2", "b3", "a2");
        assertLinearChain(a, "a1", "b1", "c1", "c2", "b3", "a2");
    }

    @Test
    void threeLevelNesting_allIncluded() {
        // A → sub_b(T,T) → branch → sub_c(T,T) → success. B: b1→b2→b3. C: c1→c2→sub_d(T,T). D: d1.
        Workflow d = singleNodeSub("SubD", "d1");
        Workflow c = workflowCWithSubD("SubC", "SubD", true, true);
        Workflow b = threeNodeSub("SubB");
        Workflow a = abcdParent("SubB", true, true, "SubC", true, true);

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(c);
        when(enrichHelper.fetchEnrichedCopy("SubD", V, TENANT)).thenReturn(d);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2", "b3", "branch_a", "groovy_true", "c1", "c2", "d1", "success_a");
        assertEquals("a1", a.getStartNode());
        assertEquals("b1", a.getStates().get("a1").getNextNode());
        assertEquals("branch_a", a.getStates().get("b3").getNextNode());
        assertBranchTargets(a, "branch_a", "groovy_true", "c1", "c1");
        assertEquals("c1", a.getStates().get("groovy_true").getNextNode());
        assertEquals("d1", a.getStates().get("c2").getNextNode());
        assertEquals("success_a", a.getStates().get("d1").getNextNode());
        assertTrue(a.getStates().get("success_a").isEnd());
    }

    @Test
    void threeLevelNesting_mixedConfigs() {
        // B(T,F): include b1,b2 skip b3. C(F,T): skip c1, include c2,d1. D(T,T): include d1.
        Workflow d = singleNodeSub("SubD", "d1");
        Workflow c = workflowCWithSubD("SubC", "SubD", true, true);
        Workflow b = threeNodeSub("SubB");
        Workflow a = abcdParent("SubB", true, false, "SubC", false, true);

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(c);
        when(enrichHelper.fetchEnrichedCopy("SubD", V, TENANT)).thenReturn(d);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2", "branch_a", "groovy_true", "c2", "d1", "success_a");
        assertEquals("b1", a.getStates().get("a1").getNextNode());
        assertEquals("branch_a", a.getStates().get("b2").getNextNode());
        assertBranchTargets(a, "branch_a", "groovy_true", "c2", "c2");
        assertEquals("c2", a.getStates().get("groovy_true").getNextNode());
        assertEquals("d1", a.getStates().get("c2").getNextNode());
        assertEquals("success_a", a.getStates().get("d1").getNextNode());
        assertTrue(a.getStates().get("success_a").isEnd());
    }

    // ========================== Group 4: Branch Rewiring ==========================

    @Test
    void branchChoice_rewiredToSubStart() {
        // branch_a choice[0] → sub_b. After flatten, choice[0] → b1.
        BranchNode branchDef = branchDef("branch_a", List.of(choice("sub_b"), choice("a3")), "a3");
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "branch_a", false),
                "branch_a", node("branch_a", branchDef, "a3", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a3", false),
                "a3", node("a3", groovyDef("a3"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "b1"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "branch_a", "b1", "a3");
        BranchNode bn = (BranchNode) a.getStates().get("branch_a").getNodeDefinition();
        assertEquals("b1", bn.getChoices().get(0).getNextNode());
        assertEquals("a3", bn.getChoices().get(1).getNextNode());
    }

    @Test
    void branchDefault_rewiredToSubStart() {
        // branch_a defaultNode = sub_b. After flatten, defaultNode → b1.
        BranchNode branchDef = branchDef("branch_a", List.of(choice("a3")), "sub_b");
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "branch_a", false),
                "branch_a", node("branch_a", branchDef, "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a3", false),
                "a3", node("a3", groovyDef("a3"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "b1"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "branch_a", "b1", "a3");
        BranchNode bn = (BranchNode) a.getStates().get("branch_a").getNodeDefinition();
        assertEquals("b1", bn.getDefaultNode());
        assertEquals("a3", bn.getChoices().get(0).getNextNode());
    }

    @Test
    void multipleReferences_allRewired() {
        // branch_a choices AND nextNode AND default all point to sub_b. All should be rewired.
        BranchNode branchDef = branchDef("branch_a", List.of(choice("sub_b"), choice("sub_b")), "sub_b");
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "branch_a", false),
                "branch_a", node("branch_a", branchDef, "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a3", false),
                "a3", node("a3", groovyDef("a3"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "b1"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "branch_a", "b1", "a3");
        BranchNode bn = (BranchNode) a.getStates().get("branch_a").getNodeDefinition();
        assertEquals("b1", bn.getChoices().get(0).getNextNode());
        assertEquals("b1", bn.getChoices().get(1).getNextNode());
        assertEquals("b1", bn.getDefaultNode());
        assertEquals("b1", a.getStates().get("branch_a").getNextNode());
    }

    // ========================== Group 5: Sub at Start / End ==========================

    @Test
    void subIsStartNode_startNodeUpdated() {
        // A: startNode=sub_b → a2(end). B: b1→b2.
        Workflow a = workflow("A", "sub_b", linkedMap(
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a2", false),
                "a2", node("a2", groovyDef("a2"), null, true)));
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertEquals("b1", a.getStartNode());
        assertStateKeys(a, "b1", "b2", "a2");
        assertLinearChain(a, "b1", "b2", "a2");
    }

    @Test
    void subIsTerminal_endFlagPropagated() {
        // A: a1 → sub_b(end=true, nextNode=null). B: b1→b2.
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), null, true)));
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2");
        assertLinearChain(a, "a1", "b1", "b2");
    }

    // ========================== Group 6: Sequential Subs ==========================

    @Test
    void sequentialSubs_bothInlined() {
        // A: a1 → sub_b → sub_c → a4(end). B: b1. C: c1.
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "sub_c", false),
                "sub_c", subNode("sub_c", subWfDef("sub_c", "SubC", true, true), "a4", false),
                "a4", node("a4", groovyDef("a4"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "b1"));
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(singleNodeSub("SubC", "c1"));

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "c1", "a4");
        assertLinearChain(a, "a1", "b1", "c1", "a4");
    }

    // ========================== Group 7: Duplicate Detection ==========================

    @Test
    void duplicateNodeName_acrossSubs_fails() {
        // A: a1 → sub_b → sub_c → a4. B has "dup". C has "dup". Should fail.
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "sub_c", false),
                "sub_c", subNode("sub_c", subWfDef("sub_c", "SubC", true, true), "a4", false),
                "a4", node("a4", groovyDef("a4"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(singleNodeSub("SubB", "dup"));
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(singleNodeSub("SubC", "dup"));

        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> flattener.flattenWorkflow(a, TENANT));
        assertEquals("SUB_WORKFLOW_DUPLICATE_NODE_NAME", ex.getType());
    }

    @Test
    void nestedInlining_noFalseDuplicate() {
        // A → sub_b. B → sub_c. C has "d1". After flatten d1 goes B→A. No false duplicate.
        Workflow c = singleNodeSub("SubC", "d1");
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "sub_c", false),
                "sub_c", subNode("sub_c", subWfDef("sub_c", "SubC", true, true), null, true)));
        Workflow a = parentWithOneSub("SubB", true, true);

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(c);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "d1", "a2");
        assertLinearChain(a, "a1", "b1", "d1", "a2");
    }

    @Test
    void defaultFailureNode_excluded_whenSameAsParent() {
        // Both parent A and sub B share the same defaultFailureNode name "default_failure".
        // After flattening, only one "default_failure" should exist (the parent's) and no error.
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true),
                "default_failure", node("default_failure", groovyDef("default_failure"), null, true)));
        b.setDefaultFailureNode("default_failure");

        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a2", false),
                "a2", node("a2", groovyDef("a2"), null, true),
                "default_failure", node("default_failure", groovyDef("default_failure"), null, true)));
        a.setDefaultFailureNode("default_failure");

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2", "a2", "default_failure");
        assertLinearChain(a, "a1", "b1", "b2", "a2");
        assertTrue(a.getStates().containsKey("default_failure"), "parent default_failure must be retained");
    }

    @Test
    void defaultFailureNode_kept_whenDifferentName() {
        // Sub has a differently-named defaultFailureNode ("sub_default_failure") — it must be inlined
        // as a regular node since it doesn't conflict with the parent's "default_failure".
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true),
                "sub_default_failure", node("sub_default_failure", groovyDef("sub_default_failure"), null, true)));
        b.setDefaultFailureNode("sub_default_failure");

        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a2", false),
                "a2", node("a2", groovyDef("a2"), null, true),
                "default_failure", node("default_failure", groovyDef("default_failure"), null, true)));
        a.setDefaultFailureNode("default_failure");

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "b2", "a2", "default_failure", "sub_default_failure");
        assertTrue(a.getStates().containsKey("sub_default_failure"),
                "sub_default_failure must be inlined since it has a different name");
    }

    @Test
    void nestedSub_defaultFailureNode_excludedAtAllLevels() {
        // A → sub_b → sub_c; all three have "default_failure" as their defaultFailureNode.
        // After flattening only the root's "default_failure" survives.
        Workflow c = workflow("SubC", "c1", linkedMap(
                "c1", node("c1", groovyDef("c1"), null, true),
                "default_failure", node("default_failure", groovyDef("default_failure"), null, true)));
        c.setDefaultFailureNode("default_failure");

        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "sub_c", false),
                "sub_c", subNode("sub_c", subWfDef("sub_c", "SubC", true, true), "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true),
                "default_failure", node("default_failure", groovyDef("default_failure"), null, true)));
        b.setDefaultFailureNode("default_failure");

        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), "a2", false),
                "a2", node("a2", groovyDef("a2"), null, true),
                "default_failure", node("default_failure", groovyDef("default_failure"), null, true)));
        a.setDefaultFailureNode("default_failure");

        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);
        when(enrichHelper.fetchEnrichedCopy("SubC", V, TENANT)).thenReturn(c);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "c1", "b2", "a2", "default_failure");
        assertEquals(1, a.getStates().entrySet().stream()
                .filter(e -> e.getKey().equals("default_failure")).count(),
                "exactly one default_failure must exist after flattening");
    }

    // ========================== Group 8: Circular / Max Depth ==========================

    @Test
    void circularDirect_fails() {
        // A has sub referencing itself.
        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_self", false),
                "sub_self", subNode("sub_self", subWfDef("sub_self", "A", true, true), null, true)));
        Workflow aCopy = workflow("A", "a1_copy", linkedMap(
                "a1_copy", node("a1_copy", groovyDef("a1_copy"), "sub_self2", false),
                "sub_self2", subNode("sub_self2", subWfDef("sub_self2", "A", true, true), null, true)));
        when(enrichHelper.fetchEnrichedCopy("A", V, TENANT)).thenReturn(aCopy);

        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> flattener.flattenWorkflow(a, TENANT));
        assertEquals("SUB_WORKFLOW_CIRCULAR_REFERENCE", ex.getType());
    }

    @Test
    void circularIndirect_fails() {
        // A → sub_b. B → sub referencing A.
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "sub_back", false),
                "sub_back", subNode("sub_back", subWfDef("sub_back", "A", true, true), null, true)));
        Workflow aCopy = workflow("A", "a1_copy", linkedMap(
                "a1_copy", node("a1_copy", groovyDef("a1_copy"), "sub_b2", false),
                "sub_b2", subNode("sub_b2", subWfDef("sub_b2", "SubB", true, true), null, true)));
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);
        when(enrichHelper.fetchEnrichedCopy("A", V, TENANT)).thenReturn(aCopy);

        Workflow a = workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", "SubB", true, true), null, true)));

        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> flattener.flattenWorkflow(a, TENANT));
        assertEquals("SUB_WORKFLOW_CIRCULAR_REFERENCE", ex.getType());
    }

    @Test
    void maxDepthExceeded_fails() {
        // 12-level chain: W0 → W1 → ... → W11. MAX_DEPTH=10, so depth 11 triggers error.
        Workflow leaf = workflow("W11", "n11", linkedMap(
                "n11", node("n11", groovyDef("n11"), null, true)));
        when(enrichHelper.fetchEnrichedCopy("W11", V, TENANT)).thenReturn(leaf);

        for (int i = 10; i >= 1; i--) {
            String wfId = "W" + i;
            String nextWfId = "W" + (i + 1);
            String subName = "sub" + i;
            Workflow wf = workflow(wfId, subName, linkedMap(
                    subName, subNode(subName, subWfDef(subName, nextWfId, true, true), null, true)));
            when(enrichHelper.fetchEnrichedCopy(wfId, V, TENANT)).thenReturn(wf);
        }

        Workflow root = workflow("W0", "sub0", linkedMap(
                "sub0", subNode("sub0", subWfDef("sub0", "W1", true, true), null, true)));

        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> flattener.flattenWorkflow(root, TENANT));
        assertEquals("SUB_WORKFLOW_MAX_DEPTH_EXCEEDED", ex.getType());
    }

    // ========================== Group 9: Special Behavior ==========================

    @Test
    public void branchIntermediateNode_notMistakenForTerminal_includeLastTrue() {
        // fake_workflow: step1 → branchNode (choices→A,B; no nextNode on wrapper) → A(end), B(end)
        // With includeLastNode=true: branchNode has nextNode=null but must NOT be treated as terminal.
        // All four nodes must be inlined and the actual end-nodes (A, B) must remain reachable.
        BranchNode branchDef = branchDef("branch_b", Arrays.asList(choice("b_A"), choice("b_B")), "b_A");
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1",      node("b1", groovyDef("b1"), "branch_b", false),
                "branch_b", node("branch_b", branchDef, null, false),  // nextNode=null on wrapper
                "b_A",     node("b_A", groovyDef("b_A"), null, true),
                "b_B",     node("b_B", groovyDef("b_B"), null, true)));
        Workflow a = parentWithOneSub("SubB", true, true);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "b1", "branch_b", "b_A", "b_B", "a2");
        assertEquals("b1", a.getStates().get("a1").getNextNode());
        assertEquals("branch_b", a.getStates().get("b1").getNextNode());
        BranchNode inlinedBranch = (BranchNode) a.getStates().get("branch_b").getNodeDefinition();
        assertEquals("b_A", inlinedBranch.getChoices().get(0).getNextNode());
        assertEquals("b_B", inlinedBranch.getChoices().get(1).getNextNode());
    }

    @Test
    public void branchIntermediateNode_notMistakenForTerminal_includeLastFalse() {
        // Convergent branch: b1 → branch_b (choices→b_A, b_B; no nextNode on wrapper)
        //                    b_A → b_success(end), b_B → b_success(end)
        // The single true terminal is b_success. With includeLastNode=false, b_success should be
        // excluded and b_A should become the effectiveEnd (findPredecessor of b_success via nextNode).
        // BEFORE FIX: findTerminal wrongly returned branch_b (nextNode=null) → excluded it →
        //             only b1 executed (first node), branch was completely bypassed. BUG.
        // AFTER FIX: findTerminal skips branch_b, finds b_success as the real terminal → b_success
        //            excluded → b1→branch_b chain is preserved.
        BranchNode bBranchDef = branchDef("branch_b", Arrays.asList(choice("b_A"), choice("b_B")), "b_A");
        Workflow b = workflow("SubB", "b1", linkedMap(
                "b1",        node("b1", groovyDef("b1"), "branch_b", false),
                "branch_b",  node("branch_b", bBranchDef, null, false),
                "b_A",       node("b_A", groovyDef("b_A"), "b_success", false),
                "b_B",       node("b_B", groovyDef("b_B"), "b_success", false),
                "b_success", node("b_success", groovyDef("b_success"), null, true)));
        Workflow a = parentWithOneSub("SubB", true, false);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        // b_success excluded (includeLastNode=false); branch_b must remain; b1 must NOT be rewired to skip branch
        assertStateKeys(a, "a1", "b1", "branch_b", "b_A", "b_B", "a2");
        assertEquals("b1", a.getStates().get("a1").getNextNode());
        assertEquals("branch_b", a.getStates().get("b1").getNextNode());
        assertTrue(a.getStates().containsKey("branch_b"));
        // effectiveEnd = b_A (first predecessor of b_success via nextNode), rewired to continue to a2
        assertEquals("a2", a.getStates().get("b_A").getNextNode());
    }


    @Test
    void branchStartNode_notExcludedByIncludeFirstFalse() {
        // B starts with BRANCH node. Config includeFirst=false. Branch should NOT be excluded.
        BranchNode bBranch = branchDef("branch_b", List.of(choice("b2")), "b2");
        Workflow b = workflow("SubB", "branch_b", linkedMap(
                "branch_b", node("branch_b", bBranch, "b2", false),
                "b2", node("b2", groovyDef("b2"), null, true)));
        Workflow a = parentWithOneSub("SubB", false, true);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertStateKeys(a, "a1", "branch_b", "b2", "a2");
        assertEquals("branch_b", a.getStates().get("a1").getNextNode());
        assertTrue(a.getStates().containsKey("branch_b"), "branch start node must not be excluded");
    }

    @Test
    void postCompletionNodes_mergedToParent() {
        Workflow b = threeNodeSub("SubB");
        b.setPostWorkflowCompletionNodes(new ArrayList<>(List.of("b1", "b2")));
        Workflow a = parentWithOneSub("SubB", true, true);
        when(enrichHelper.fetchEnrichedCopy("SubB", V, TENANT)).thenReturn(b);

        flattener.flattenWorkflow(a, TENANT);

        assertNotNull(a.getPostWorkflowCompletionNodes());
        assertTrue(a.getPostWorkflowCompletionNodes().contains("b1"));
        assertTrue(a.getPostWorkflowCompletionNodes().contains("b2"));
    }

    // ========================== Workflow Builders ==========================

    private static Workflow workflow(String id, String startNode, Map<String, WorkflowNode> states) {
        Workflow wf = new Workflow();
        wf.setId(id);
        wf.setStartNode(startNode);
        wf.setVersion(V);
        wf.setStates(states);
        return wf;
    }

    /** Parent A: a1 → sub_b → a2(end) */
    private static Workflow parentWithOneSub(String subWfId, boolean includeFirst, boolean includeLast) {
        return workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", subWfId, includeFirst, includeLast), "a2", false),
                "a2", node("a2", groovyDef("a2"), null, true)));
    }

    /** B: b1 → b2 → b3(end) */
    private static Workflow threeNodeSub(String id) {
        return workflow(id, "b1", linkedMap(
                "b1", node("b1", groovyDef("b1"), "b2", false),
                "b2", node("b2", groovyDef("b2"), "b3", false),
                "b3", node("b3", groovyDef("b3"), null, true)));
    }

    /** Single-node sub: nodeName(end) */
    private static Workflow singleNodeSub(String wfId, String nodeName) {
        return workflow(wfId, nodeName, linkedMap(
                nodeName, node(nodeName, groovyDef(nodeName), null, true)));
    }

    /** C: c1 → c2 → sub_d(end) */
    private static Workflow workflowCWithSubD(String cId, String dId, boolean dFirst, boolean dLast) {
        return workflow(cId, "c1", linkedMap(
                "c1", node("c1", groovyDef("c1"), "c2", false),
                "c2", node("c2", groovyDef("c2"), "sub_d", false),
                "sub_d", subNode("sub_d", subWfDef("sub_d", dId, dFirst, dLast), null, true)));
    }

    /** ABCD parent: a1 → sub_b → branch_a([groovy_true, sub_c], default=sub_c) → sub_c → success_a(end) */
    private static Workflow abcdParent(String subBId, boolean bF, boolean bL,
                                       String subCId, boolean cF, boolean cL) {
        BranchNode branch = branchDef("branch_a", List.of(choice("groovy_true"), choice("sub_c")), "sub_c");
        return workflow("A", "a1", linkedMap(
                "a1", node("a1", groovyDef("a1"), "sub_b", false),
                "sub_b", subNode("sub_b", subWfDef("sub_b", subBId, bF, bL), "branch_a", false),
                "branch_a", node("branch_a", branch, "sub_c", false),
                "groovy_true", node("groovy_true", groovyDef("groovy_true"), "sub_c", false),
                "sub_c", subNode("sub_c", subWfDef("sub_c", subCId, cF, cL), "success_a", false),
                "success_a", node("success_a", successDef("success_a"), null, true)));
    }

    // ========================== Node Builders ==========================

    private static WorkflowNode node(String name, NodeDefinition def, String nextNode, boolean end) {
        WorkflowNode wn = new WorkflowNode();
        wn.setInstanceName(name);
        wn.setResourceId(name);
        wn.setResourceVersion(V);
        wn.setType(WorkflowNodeType.NODE);
        wn.setNextNode(nextNode);
        wn.setEnd(end);
        wn.setNodeDefinition(def);
        return wn;
    }

    private static WorkflowNode subNode(String name, SubWorkflowNode def, String nextNode, boolean end) {
        WorkflowNode wn = new WorkflowNode();
        wn.setInstanceName(name);
        wn.setResourceId(name);
        wn.setResourceVersion(V);
        wn.setType(WorkflowNodeType.SUBWORKFLOW);
        wn.setNextNode(nextNode);
        wn.setEnd(end);
        wn.setNodeDefinition(def);
        return wn;
    }

    private static GroovyNode groovyDef(String id) {
        GroovyNode n = new GroovyNode();
        n.setId(id);
        n.setName(id);
        return n;
    }

    private static SubWorkflowNode subWfDef(String id, String subWfId, boolean includeFirst, boolean includeLast) {
        SubWorkflowNode n = new SubWorkflowNode();
        n.setId(id);
        n.setName(id);
        n.setSubWorkflowId(subWfId);
        n.setSubWorkflowVersion(V);
        n.setConfig(SubWorkflowConfig.builder()
                .includeFirstNode(includeFirst)
                .includeLastNode(includeLast)
                .build());
        return n;
    }

    private static SuccessNode successDef(String id) {
        SuccessNode n = new SuccessNode();
        n.setId(id);
        n.setName(id);
        return n;
    }

    private static BranchNode branchDef(String id, List<BranchComponents> choices, String defaultNode) {
        BranchNode n = new BranchNode();
        n.setId(id);
        n.setName(id);
        n.setChoices(choices);
        n.setDefaultNode(defaultNode);
        return n;
    }

    private static BranchComponents choice(String nextNode) {
        BranchComponents bc = new BranchComponents();
        bc.setNextNode(nextNode);
        return bc;
    }

    // ========================== Map / Assertion Helpers ==========================

    private static LinkedHashMap<String, WorkflowNode> linkedMap(Object... kv) {
        LinkedHashMap<String, WorkflowNode> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (WorkflowNode) kv[i + 1]);
        }
        return map;
    }

    private static void assertStateKeys(Workflow wf, String... expected) {
        assertEquals(Set.of(expected), wf.getStates().keySet());
    }

    private static void assertLinearChain(Workflow wf, String... names) {
        String current = wf.getStartNode();
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], current, "chain mismatch at position " + i);
            WorkflowNode n = wf.getStates().get(current);
            assertNotNull(n, "missing node: " + current);
            if (i < names.length - 1) {
                assertFalse(n.isEnd(), current + " should not be end");
                current = n.getNextNode();
            } else {
                assertTrue(n.isEnd(), current + " should be end");
            }
        }
    }

    private static void assertBranchTargets(Workflow wf, String branchName,
                                             String c0Next, String c1Next, String defaultNext) {
        BranchNode bn = (BranchNode) wf.getStates().get(branchName).getNodeDefinition();
        assertEquals(c0Next, bn.getChoices().get(0).getNextNode(), "branch choice[0]");
        assertEquals(c1Next, bn.getChoices().get(1).getNextNode(), "branch choice[1]");
        assertEquals(defaultNext, bn.getDefaultNode(), "branch default");
    }
}
