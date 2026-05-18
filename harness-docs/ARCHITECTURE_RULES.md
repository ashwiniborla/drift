# ARCHITECTURE_RULES.md — Drift Dependency and Layer Enforcement

## Module Dependency Rules

The Drift codebase enforces a strict layering hierarchy. These rules are verified by ArchUnit tests
(to be added in `commons/src/test/java`) and reviewed in code review.

### Allowed Dependencies

```
java-sdk   →  (nothing internal)
commons    →  java-sdk only
api        →  commons, java-sdk
worker     →  commons, java-sdk
```

### Forbidden Dependencies (violations = build failure)

| From | To | Reason |
|------|----|--------|
| `java-sdk` | `commons` | java-sdk is the public contract; it cannot depend on internal modules |
| `java-sdk` | `api` | Same — public SDK must be dependency-free |
| `java-sdk` | `worker` | Same |
| `commons` | `api` | commons is infrastructure; api is a consumer |
| `commons` | `worker` | Same |
| `api` | `worker` | Services must not directly depend on each other |
| `worker` | `api` | Same |

### Package-Level Rules (within modules)

**api module:**
- `resources` layer may depend on `service` layer
- `service` layer may depend on `commons` persistence (DAOs, caches)
- `service` layer must NOT depend on `resources` (no circular imports)
- `bootstrap` may wire everything but no other layer may depend on `bootstrap`

**worker module:**
- `workflows` layer must be **deterministic** — must NOT import java.util.Random, System.currentTimeMillis, java.io.*, java.net.* directly
- `activities` layer may do I/O
- `workflows` layer must NOT import from `activities` directly (use activity stubs only)
- `executor` layer is used only by `activities`
- `translator` layer is used only by `activities`

**commons module:**
- `model` package must NOT import from `persistence` (models are pure data)
- `persistence` package may import from `model`
- No Spring annotations anywhere

## ArchUnit Test Skeleton

Add this to `commons/src/test/java/com/flipkart/drift/ArchitectureTest.java`:

```java
package com.flipkart.drift;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    @Test
    void models_should_not_depend_on_persistence() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.flipkart.drift.commons.model..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.flipkart.drift.persistence..");

        rule.check(new ClassFileImporter().importPackages("com.flipkart.drift"));
    }

    @Test
    void no_spring_annotations_anywhere() {
        ArchRule rule = noClasses()
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .orShould().beAnnotatedWith("org.springframework.boot.autoconfigure.SpringBootApplication");

        rule.check(new ClassFileImporter().importPackages("com.flipkart.drift"));
    }
}
```

## Enforcement Status

| Rule | Status | Tool |
|------|--------|------|
| No upward module imports | Enforced by Maven scope (compile-time) | Maven dependency declarations |
| No Spring annotations | Manual review | ArchUnit (to be added) |
| Workflow code determinism | Convention | Code review |
| No direct HBase outside AbstractEntityDao | Convention | ArchUnit (to be added) |
| Test pattern `**/*Test.java` / `**/*IT.java` | Enforced | maven-surefire-plugin config |
