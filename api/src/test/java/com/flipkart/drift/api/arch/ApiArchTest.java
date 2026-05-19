package com.flipkart.drift.api.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests that enforce module boundary rules for the API service.
 *
 * Rules enforced:
 *   - api must NOT import classes from the worker module
 *   - REST resources must live in the .resources sub-package
 *   - Exception mappers must live in the .exception sub-package
 */
class ApiArchTest {

    private static JavaClasses API_CLASSES;

    @BeforeAll
    static void importClasses() {
        API_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.flipkart.drift.api");
    }

    /**
     * Rule 1: API must not depend on the Worker module.
     * api and worker are sibling services — they must not import each other.
     */
    @Test
    void api_should_not_depend_on_worker() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.api..")
                .should().dependOnClassesThat().resideInAPackage("com.flipkart.drift.worker..");

        rule.check(API_CLASSES);
    }

    /**
     * Rule 2: REST resource classes must reside in the .resources package.
     * Classes annotated with @Path (JAX-RS) must be in the resources sub-package.
     */
    @Test
    void jaxrs_resources_should_reside_in_resources_package() {
        ArchRule rule = noClasses()
                .that().areAnnotatedWith("javax.ws.rs.Path")
                .should().resideOutsideOfPackage("com.flipkart.drift.api.resources..");

        rule.check(API_CLASSES);
    }

    /**
     * Rule 3: Exception mappers must reside in the .exception package.
     */
    @Test
    void exception_mappers_should_reside_in_exception_package() {
        ArchRule rule = noClasses()
                .that().implement("javax.ws.rs.ext.ExceptionMapper")
                .should().resideOutsideOfPackage("com.flipkart.drift.api.exception..");

        rule.check(API_CLASSES);
    }
}
