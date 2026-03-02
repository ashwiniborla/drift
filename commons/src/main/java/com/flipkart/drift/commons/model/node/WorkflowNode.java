package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.WorkflowNodeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowNode {
    private String instanceName;
    private String resourceId;
    private String resourceVersion;
    private WorkflowNodeType type;
    private Map<String, String> parameters;
    private String contextOverrideKey;
    private String nextNode;
    private boolean end;
    NodeDefinition nodeDefinition; // This is populated while fetching WorkflowDefinition in FetchWorkflowActivity

    /**
     * Returns the key used to store this node's result in workflow context.
     * Uses {@link #contextOverrideKey} when set (e.g. for inlined sub-workflow nodes), otherwise {@link #instanceName}.
     */
    public String getNodeIdentifier() {
        return contextOverrideKey != null ? contextOverrideKey : instanceName;
    }
}
