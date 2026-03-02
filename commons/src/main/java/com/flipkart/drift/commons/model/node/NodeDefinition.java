package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;


@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpNode.class, name = "HTTP"),
        @JsonSubTypes.Type(value = GroovyNode.class, name = "GROOVY"),
        @JsonSubTypes.Type(value = BranchNode.class, name = "BRANCH"),
        @JsonSubTypes.Type(value = SuccessNode.class, name = "SUCCESS"),
        @JsonSubTypes.Type(value = FailureNode.class, name = "FAILURE"),
        @JsonSubTypes.Type(value = InstructionNode.class, name = "INSTRUCTION"),
        @JsonSubTypes.Type(value = ProcessorNode.class, name = "PROCESSOR"),
        @JsonSubTypes.Type(value = ContextOverrideNode.class, name = "CONTEXT_OVERRIDE"),
        @JsonSubTypes.Type(value = DelegateNode.class, name = "DELEGATE"),
        @JsonSubTypes.Type(value = ChildNode.class, name = "CHILD"),
        @JsonSubTypes.Type(value = WaitNode.class, name = "WAIT"),
        @JsonSubTypes.Type(value = SubWorkflowNode.class, name = "SUB_WORKFLOW"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class NodeDefinition {
    private String id;
    private String name;
    private NodeType type;
    private List<String> parameters;
    private String version;

    public void validateWFNodeFields() {
        if (StringUtils.isEmpty(id)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "id can't be empty");
        }
        if (StringUtils.isEmpty(name)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "nodeName can't be empty");
        }
        if (Objects.isNull(type)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "nodeType shouldn't be null");
        }
    }

    public abstract NodeType getType();

    public abstract void mergeRequestToEntity(NodeDefinition sourceNode);
}
