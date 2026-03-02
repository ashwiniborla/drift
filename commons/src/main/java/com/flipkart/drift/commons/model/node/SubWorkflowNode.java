package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SubWorkflowNode extends NodeDefinition {
    @NotBlank(message = "subWorkflowId can't be blank")
    private String subWorkflowId;
    @NotBlank(message = "subWorkflowVersion can't be empty")
    private String subWorkflowVersion;
    @Valid
    private SubWorkflowConfig config;

    @Override
    public NodeType getType() {
        return NodeType.SUB_WORKFLOW;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        SubWorkflowNode source = (SubWorkflowNode) sourceNode;
        if (source.getSubWorkflowId() != null) {
            this.setSubWorkflowId(source.getSubWorkflowId());
        }
        if (source.getSubWorkflowVersion() != null) {
            this.setSubWorkflowVersion(source.getSubWorkflowVersion());
        }
        if (source.getConfig() != null) {
            this.setConfig(source.getConfig());
        }
    }

    public SubWorkflowConfig getEffectiveConfig() {
        return config != null ? config : SubWorkflowConfig.builder().build();
    }
}
