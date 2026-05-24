package com.flipkart.drift.worker.task;

import com.flipkart.drift.persistence.cache.NodeDefinitionCache;
import com.flipkart.drift.persistence.cache.WorkflowCache;
import io.dropwizard.servlets.tasks.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Dropwizard admin Task registered on port 7201.
 * Handles POST /tasks/cache-invalidate?type=NODE|WORKFLOW&amp;key=&lt;rowKey&gt;
 *
 * Called by WorkerCacheInvalidationClient (api module) after each DSL publish to HBase.
 * feature=redis-removal
 */
@Slf4j
public class CacheInvalidationTask extends Task {

    private static final String TYPE_NODE = "NODE";
    private static final String TYPE_WORKFLOW = "WORKFLOW";

    private final NodeDefinitionCache nodeDefinitionCache;
    private final WorkflowCache workflowCache;

    public CacheInvalidationTask(NodeDefinitionCache nodeDefinitionCache, WorkflowCache workflowCache) {
        super("cache-invalidate");
        this.nodeDefinitionCache = nodeDefinitionCache;
        this.workflowCache = workflowCache;
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
        String type = null;
        String key = null;

        try {
            // Dropwizard Task convention: parameters.get("type") returns List<String>
            List<String> typeList = parameters.get("type");
            List<String> keyList = parameters.get("key");

            if (typeList == null || typeList.isEmpty()) {
                output.println("ERROR: type parameter is required (NODE or WORKFLOW)");
                return;
            }
            if (keyList == null || keyList.isEmpty()) {
                output.println("ERROR: key parameter is required");
                return;
            }

            type = typeList.get(0);
            key = keyList.get(0);

            if (!TYPE_NODE.equals(type) && !TYPE_WORKFLOW.equals(type)) {
                output.println("ERROR: unknown type: " + type + " (must be NODE or WORKFLOW)");
                return;
            }

            if (StringUtils.isBlank(key)) {
                output.println("ERROR: key is required");
                return;
            }

            switch (type) {
                case TYPE_NODE:
                    nodeDefinitionCache.invalidate(key);
                    break;
                case TYPE_WORKFLOW:
                    workflowCache.invalidate(key);
                    break;
                default:
                    output.println("ERROR: unknown type: " + type);
                    return;
            }

            // PROBE::redis-removal-worker-bootstrap::INFO
            log.info("operation=cacheInvalidate feature=redis-removal type={} key={} result=INVALIDATED", type, key);
            output.println("OK: invalidated " + type + " key=" + key);

        } catch (Exception e) {
            log.error("operation=cacheInvalidate feature=redis-removal type={} key={} error={}", type, key, e.getMessage(), e);
            output.println("ERROR: " + e.getMessage());
        }
    }
}
