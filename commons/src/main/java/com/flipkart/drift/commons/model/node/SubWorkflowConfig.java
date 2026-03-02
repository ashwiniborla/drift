package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.ErrorHandlingStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubWorkflowConfig {
    @Builder.Default
    private Boolean includeLastNode = true;
    @Builder.Default
    private Boolean includeFirstNode = true;

    /**
     * How failures inside this inlined sub-workflow are handled at runtime.
     * <ul>
     *   <li><b>PROPAGATE</b> (default): Use the parent (root) workflow's defaultFailureNode. No code reads this value;
     *       the executor already uses the flattened workflow's single defaultFailureNode, which is the root's.</li>
     *   <li><b>ISOLATE</b>: Prefer the sub-workflow's own defaultFailureNode (not yet implemented; a warning is logged and behaviour is PROPAGATE).</li>
     * </ul>
     *
     * @see ErrorHandlingStrategy
     */
    @Builder.Default
    private ErrorHandlingStrategy errorHandlingStrategy = ErrorHandlingStrategy.PROPAGATE;

    public boolean isIncludeLastNode() {
        return includeLastNode != null ? includeLastNode : true;
    }

    public boolean isIncludeFirstNode() {
        return includeFirstNode != null ? includeFirstNode : true;
    }

    public ErrorHandlingStrategy getErrorHandlingStrategy() {
        return errorHandlingStrategy != null ? errorHandlingStrategy : ErrorHandlingStrategy.PROPAGATE;
    }
}
