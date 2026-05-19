package com.flipkart.drift.worker.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * ArchUnit tests that enforce module boundary rules for the Worker service.
 *
 * Rules enforced:
 *   - worker must NOT import classes from the api module
 *   - Temporal workflow implementations must not call System.currentTimeMillis()
 *   - Temporal workflow implementations must not call Thread.sleep()
 */
class WorkerArchTest {

    private static JavaClasses WORKER_CLASSES;

    @BeforeAll
    static void importClasses() {
        WORKER_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.flipkart.drift.worker");
    }

    /**
     * Rule 1: Worker must not depend on the API module.
     * api and worker are sibling services — they must not import each other.
     */
    @Test
    void worker_should_not_depend_on_api() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.worker..")
                .should().dependOnClassesThat().resideInAPackage("com.flipkart.drift.api..");

        rule.check(WORKER_CLASSES);
    }

    /**
     * Rule 2: Temporal workflow implementations must not call System.currentTimeMillis().
     * Temporal workflows must be deterministic — use Workflow.currentTimeMillis() instead.
     */
    @Test
    void temporal_workflows_should_not_call_system_current_time_millis() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.worker.workflows..")
                .should().callMethod(System.class, "currentTimeMillis");

        rule.check(WORKER_CLASSES);
    }

    /**
     * Rule 3: Temporal workflow implementations must not call Thread.sleep().
     * Use Workflow.sleep() instead — Thread.sleep() breaks Temporal's replay semantics.
     */
    @Test
    void temporal_workflows_should_not_call_thread_sleep() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.worker.workflows..")
                .should().callMethod(Thread.class, "sleep", long.class);

        rule.check(WORKER_CLASSES);
    }

    /**
     * Rule 4: Direct HBase Table access is forbidden outside the DAO layer.
     * All HBase access must go through the DAO classes in commons.persistence.dao.
     */
    @Test
    void worker_should_not_access_hbase_table_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.worker..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.apache.hadoop.hbase.client.Table");

        rule.check(WORKER_CLASSES);
    }
}
