package com.flipkart.drift.sdk.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests that enforce module boundary rules for the java-sdk library.
 *
 * java-sdk is the foundation layer (layer 1). It must not import from any
 * other internal Drift module.
 *
 * Rules enforced:
 *   - java-sdk must NOT import from commons
 *   - java-sdk must NOT import from api
 *   - java-sdk must NOT import from worker
 */
class JavaSdkArchTest {

    private static JavaClasses SDK_CLASSES;

    @BeforeAll
    static void importClasses() {
        SDK_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.flipkart.drift.sdk");
    }

    /**
     * Rule 1: java-sdk must not depend on the commons module.
     * sdk is the foundation — it cannot import from a layer above itself.
     */
    @Test
    void sdk_should_not_depend_on_commons() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.sdk..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.flipkart.drift.commons..",
                        "com.flipkart.drift.persistence..",
                        "com.flipkart.drift.workflows.."
                );

        rule.check(SDK_CLASSES);
    }

    /**
     * Rule 2: java-sdk must not depend on the api module.
     */
    @Test
    void sdk_should_not_depend_on_api() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.sdk..")
                .should().dependOnClassesThat().resideInAPackage("com.flipkart.drift.api..");

        rule.check(SDK_CLASSES);
    }

    /**
     * Rule 3: java-sdk must not depend on the worker module.
     */
    @Test
    void sdk_should_not_depend_on_worker() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.flipkart.drift.sdk..")
                .should().dependOnClassesThat().resideInAPackage("com.flipkart.drift.worker..");

        rule.check(SDK_CLASSES);
    }

    /**
     * Rule 4: SPI implementations must reside in the .spi package.
     * Classes that implement SPI interfaces must be in the spi sub-package.
     */
    @Test
    void spi_implementations_should_reside_in_spi_package() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.flipkart.drift.sdk.spi..")
                .should().implement("com.flipkart.drift.sdk.spi.ab.ABTestingProvider");

        rule.check(SDK_CLASSES);
    }
}
