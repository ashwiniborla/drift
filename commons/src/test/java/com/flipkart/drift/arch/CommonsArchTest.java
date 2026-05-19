package com.flipkart.drift.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests that enforce module boundary rules for the commons library.
 *
 * Rules enforced:
 *   - commons must NOT import classes from the api module
 *   - commons must NOT import classes from the worker module
 *   - HBase Table access must only be in persistence.dao sub-packages
 */
class CommonsArchTest {

    private static JavaClasses COMMONS_CLASSES;

    @BeforeAll
    static void importClasses() {
        COMMONS_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.flipkart.drift.commons",
                        "com.flipkart.drift.persistence",
                        "com.flipkart.drift.workflows"
                );
    }

    /**
     * Rule 1: commons must not depend on the API module.
     * commons is layer 2; api is layer 3. No upward imports allowed.
     */
    @Test
    void commons_should_not_depend_on_api() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.flipkart.drift.commons..",
                        "com.flipkart.drift.persistence..",
                        "com.flipkart.drift.workflows.."
                )
                .should().dependOnClassesThat().resideInAPackage("com.flipkart.drift.api..");

        rule.check(COMMONS_CLASSES);
    }

    /**
     * Rule 2: commons must not depend on the Worker module.
     * commons is layer 2; worker is layer 3. No upward imports allowed.
     */
    @Test
    void commons_should_not_depend_on_worker() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.flipkart.drift.commons..",
                        "com.flipkart.drift.persistence..",
                        "com.flipkart.drift.workflows.."
                )
                .should().dependOnClassesThat().resideInAPackage("com.flipkart.drift.worker..");

        rule.check(COMMONS_CLASSES);
    }

    /**
     * Rule 3: Direct HBase Table access must only exist in the DAO layer.
     * Service classes (cache, utils) must not call HBase directly.
     */
    @Test
    void hbase_table_access_must_be_in_dao_layer() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.flipkart.drift.persistence.dao..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.apache.hadoop.hbase.client.Table");

        rule.check(COMMONS_CLASSES);
    }
}
