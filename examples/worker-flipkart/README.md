# Drift Worker - Custom Implementation Guide

This guide helps you create your own custom worker implementation with organization-specific integrations (A/B testing, authentication, etc.).

---

## Table of Contents

1. [When Do You Need a Custom Worker?](#when-do-you-need-a-custom-worker)
2. [Module Structure](#module-structure)
3. [POM Configuration](#pom-configuration)
4. [Implementing SPIs](#implementing-spis)
5. [Local Testing in IntelliJ](#local-testing-in-intellij)
6. [Docker Packaging](#docker-packaging)
7. [Deployment](#deployment)

---

## When Do You Need a Custom Worker?

Create a custom worker extension when you need to integrate **organization-specific** services:

### **Common Use Cases:**

#### **1. Custom Authentication (TokenProvider)**
- **Purpose:** Provides bearer tokens for HTTP node calls
- **Example:** Your organization uses an internal auth service (Okta, Auth0, custom IAM)
- **What it does:** The `TokenProvider` is called by HTTP nodes to get authentication tokens before making external API calls

```java
// HTTP Node execution flow:
HTTPNode.execute() 
  → TokenProvider.getAuthToken("target-service-id")
  → Returns: "Bearer eyJhbGc..."
  → Adds to HTTP request headers
```

#### **2. Custom A/B Testing (ABTestingProvider)**
- **Purpose:** Determines which workflow version to execute (control vs treatment)
- **Example:** Your organization uses an internal experimentation platform
- **What it does:** Routes traffic between different workflow versions based on experiment configuration

```java
// Workflow execution flow:
WorkflowRouter.getVersion(workflowId, customerId)
  → ABTestingProvider.isInTreatment(customerId, "experiment-123", "variable-X")
  → Returns: true (treatment) or false (control)
  → Executes corresponding workflow version
```
---

## Module Structure

### **Recommended Directory Layout:**

```
worker-<your-org>/
├── pom.xml                          # Standalone POM (no parent)
├── Dockerfile                       # Extends base drift-worker image
├── README.md                        # This file
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/<your-org>/drift/worker/<org>/
│   │   │       ├── ab/
│   │   │       │   └── CustomABTestingProvider.java #(Optional)
│   │   │       └── auth/
│   │   │           └── CustomTokenProvider.java #(Optional)
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── services/           # SPI registration files #(Optional)
│   │       │       ├── com.flipkart.drift.sdk.spi.ab.ABTestingProvider
│   │       │       └── com.flipkart.drift.sdk.spi.auth.TokenProvider
│   │       └── scripts/                # Custom Groovy scripts (optional), this contains the groovy scripts referred by the node specs
│   │           └── custom-script.groovy
│   └── test/
│       └── java/
│           └── com/<your-org>/drift/worker/<org>/
│               └── *Test.java
```

### **Key Principles:**

1. **Standalone Module** - No dependency on base worker
2. **Minimal Dependencies** - Only depend on `java-sdk` (provided scope)
3. **Organization Isolation** - Use your org's package naming
4. **SPI-based** - Integration via Service Provider Interface pattern

---

## POM Configuration

### **Basic POM Structure:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Your organization's coordinates -->
    <groupId>com.yourorg.drift</groupId>
    <artifactId>worker-yourorg</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <name>Drift Worker - YourOrg Integration</name>
    <description>YourOrg-specific implementations for Drift Worker</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- ONLY depend on java-sdk for SPI interfaces -->
        <dependency>
            <groupId>com.flipkart.drift</groupId>
            <artifactId>java-sdk</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>provided</scope>  <!-- Provided by base worker at runtime -->
        </dependency>

        <!-- Your organization's internal libraries -->
        <dependency>
            <groupId>com.yourorg</groupId>
            <artifactId>auth-client</artifactId>
            <version>2.0.0</version>
        </dependency>
        
        <dependency>
            <groupId>com.yourorg</groupId>
            <artifactId>ab-testing-sdk</artifactId>
            <version>1.5.0</version>
        </dependency>

        <!-- Common utilities (if needed) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.30</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compiler -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <release>17</release>
                </configuration>
            </plugin>

            <!-- Shade Plugin: Create fat JAR with dependencies -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <createDependencyReducedPom>false</createDependencyReducedPom>
                    <filters>
                        <filter>
                            <artifact>*:*</artifact>
                            <excludes>
                                <exclude>META-INF/*.SF</exclude>
                                <exclude>META-INF/*.DSA</exclude>
                                <exclude>META-INF/*.RSA</exclude>
                            </excludes>
                        </filter>
                    </filters>
                    
                    <!-- CRITICAL: Exclude libraries already in base worker -->
                    <artifactSet>
                        <excludes>
                            <!-- Jackson - Dropwizard provides these -->
                            <exclude>com.fasterxml.jackson.core:*</exclude>
                            <exclude>com.fasterxml.jackson.datatype:*</exclude>
                            <exclude>com.fasterxml.jackson.dataformat:*</exclude>
                            <exclude>com.fasterxml.jackson.module:*</exclude>
                            
                            <!-- Common libraries in base worker -->
                            <exclude>com.google.guava:guava</exclude>
                            <exclude>org.slf4j:*</exclude>
                            <exclude>ch.qos.logback:*</exclude>
                            <exclude>commons-lang:commons-lang</exclude>
                            <exclude>commons-codec:commons-codec</exclude>
                            <exclude>commons-logging:commons-logging</exclude>
                            <exclude>commons-configuration:commons-configuration</exclude>
                            <exclude>com.netflix.archaius:archaius-core</exclude>
                            <exclude>commons-collections:commons-collections</exclude>
                            
                            <!-- HTTP clients in base worker -->
                            <exclude>org.apache.httpcomponents:*</exclude>
                            
                            <!-- Metrics - base worker has Dropwizard Metrics 4.x -->
                            <exclude>com.codahale.metrics:*</exclude>
                            <exclude>io.dropwizard.metrics:*</exclude>
                            <exclude>io.dropwizard:dropwizard-metrics</exclude>
                            
                            <!-- Java SDK is provided -->
                            <exclude>com.flipkart.drift:java-sdk</exclude>
                        </excludes>
                    </artifactSet>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <!-- Merge SPI service files -->
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                            
                            <!-- Package relocation for conflicting dependencies -->
                            <relocations>
                                <!-- Example: If your lib needs different SnakeYAML version -->
                                <relocation>
                                    <pattern>org.yaml.snakeyaml</pattern>
                                    <shadedPattern>com.yourorg.drift.shaded.snakeyaml</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### **Key POM Considerations:**

#### **1. ⚠️ CRITICAL: Exclude Common Libraries**

The base `drift-worker` already includes these libraries. **DO NOT** bundle them in your extension JAR to avoid version conflicts:

| Library Category | Why Exclude | Impact if Included |
|-----------------|-------------|-------------------|
| Jackson | Dropwizard provides 2.15.x | `NoSuchMethodError` in YAML parsing |
| SnakeYAML | Dropwizard provides 1.30+ | Constructor mismatch errors |
| Guava | Commons/Worker provides it | Potential binary incompatibility |
| SLF4J/Logback | Dropwizard logging stack | Multiple bindings warnings |
| Metrics | Dropwizard Metrics 4.x | `NoSuchMethodError` in metrics |
| HTTP Clients | Commons provides Apache HTTP | Connection pool conflicts |
| Commons libraries | Shared across modules | ClassCastException potential |

#### **2. When to Use Package Relocation:**

Use `<relocations>` when your organization's library requires a **different version** of a common library than the base worker:

**Example Scenario:**
```
Base Worker: Uses SnakeYAML 1.30+
Your Auth Library: Requires SnakeYAML 1.26
```

**Solution:**
```xml
<relocations>
    <relocation>
        <pattern>org.yaml.snakeyaml</pattern>
        <shadedPattern>com.yourorg.drift.shaded.snakeyaml</shadedPattern>
    </relocation>
</relocations>
```

This **renames** the package in bytecode so both versions coexist without conflict.

#### **3. Dependency Scope Strategy:**

```xml
<!-- SDK interfaces: provided (comes from base worker) -->
<dependency>
    <groupId>com.flipkart.drift</groupId>
    <artifactId>java-sdk</artifactId>
    <scope>provided</scope>
</dependency>

<!-- Your org's libraries: compile (bundled in extension JAR) -->
<dependency>
    <groupId>com.yourorg</groupId>
    <artifactId>auth-client</artifactId>
    <scope>compile</scope>  <!-- Default -->
</dependency>

<!-- Build-time only: provided -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

---

## Implementing SPIs

### **1. TokenProvider Implementation**

**Purpose:** Provides authentication tokens for HTTP nodes to call external services.

```java
package com.yourorg.drift.worker.yourorg.auth;

import com.flipkart.drift.sdk.spi.auth.TokenProvider;
import com.yourorg.auth.AuthClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom token provider using YourOrg's authentication service.
 * 
 * This provider is called by HTTP nodes when they need to authenticate
 * requests to external services.
 */
@Slf4j
public class YourOrgTokenProvider implements TokenProvider {
    private AuthClient authClient;
    private boolean initialized = false;

    /**
     * REQUIRED: Public no-arg constructor for ServiceLoader.
     * ServiceLoader uses reflection to instantiate this class.
     */
    public YourOrgTokenProvider() {
        // Empty constructor - initialization happens in init()
    }

    @Override
    public void init() {
        if (initialized) {
            log.debug("YourOrgTokenProvider already initialized");
            return;
        }

        try {
            // Initialize your auth client
            // You can read configuration from:
            // 1. Environment variables
            // 2. DynamicPropertyFactory (Archaius)
            // 3. System properties
            
            String authUrl = System.getenv("AUTH_SERVICE_URL");
            String clientId = System.getenv("AUTH_CLIENT_ID");
            String clientSecret = System.getenv("AUTH_CLIENT_SECRET");
            
            if (authUrl == null || clientId == null || clientSecret == null) {
                throw new IllegalStateException(
                    "Missing required environment variables: " +
                    "AUTH_SERVICE_URL, AUTH_CLIENT_ID, AUTH_CLIENT_SECRET"
                );
            }

            log.info("Initializing YourOrg AuthClient with URL: {}", authUrl);
            
            this.authClient = AuthClient.builder()
                .serviceUrl(authUrl)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
            
            initialized = true;
            log.info("YourOrgTokenProvider initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize YourOrgTokenProvider", e);
            throw new RuntimeException("TokenProvider initialization failed", e);
        }
    }

    @Override
    public String getAuthToken(String targetServiceId) {
        if (!initialized) {
            log.error("YourOrgTokenProvider not initialized");
            return "";  // Return empty string on error
        }

        try {
            // Fetch token from your auth service
            String token = authClient.getToken(targetServiceId);
            log.debug("Generated auth token for service: {}", targetServiceId);
            return "Bearer " + token;  // Return in Authorization header format
            
        } catch (Exception e) {
            log.error("Failed to get auth token for service: {}", targetServiceId, e);
            return "";  // Return empty string on error
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
```

### **2. ABTestingProvider Implementation**

**Purpose:** Determines which workflow version to execute based on A/B test configuration.

```java
package com.yourorg.drift.worker.yourorg.ab;

import com.flipkart.drift.sdk.spi.ab.ABTestingProvider;
import com.yourorg.experimentation.ExperimentClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom A/B testing provider using YourOrg's experimentation platform.
 * 
 * This provider is called during workflow routing to determine if a user
 * should get the treatment (new version) or control (old version) of a workflow.
 */
@Slf4j
public class YourOrgABTestingProvider implements ABTestingProvider {
    private ExperimentClient experimentClient;
    private boolean initialized = false;

    /**
     * REQUIRED: Public no-arg constructor for ServiceLoader.
     */
    public YourOrgABTestingProvider() {
        // Empty constructor
    }

    @Override
    public void init() {
        if (initialized) {
            log.debug("YourOrgABTestingProvider already initialized");
            return;
        }

        try {
            String experimentUrl = System.getenv("EXPERIMENT_SERVICE_URL");
            String apiKey = System.getenv("EXPERIMENT_API_KEY");
            
            if (experimentUrl == null || apiKey == null) {
                throw new IllegalStateException(
                    "Missing required environment variables: " +
                    "EXPERIMENT_SERVICE_URL, EXPERIMENT_API_KEY"
                );
            }

            log.info("Initializing YourOrg ExperimentClient with URL: {}", experimentUrl);
            
            this.experimentClient = ExperimentClient.builder()
                .serviceUrl(experimentUrl)
                .apiKey(apiKey)
                .build();
            
            initialized = true;
            log.info("YourOrgABTestingProvider initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize YourOrgABTestingProvider", e);
            throw new RuntimeException("ABTestingProvider initialization failed", e);
        }
    }

    @Override
    public boolean isInTreatment(String pivotValue, String experimentName, String variableName) {
        if (!initialized) {
            log.warn("YourOrgABTestingProvider not initialized, returning control");
            return false;  // Default to control (old version)
        }

        try {
            // Call your experimentation service
            boolean result = experimentClient.isInTreatment(
                pivotValue,      // Usually: customerId, userId, sessionId
                experimentName,  // e.g., "checkout_redesign_v2"
                variableName     // e.g., "show_new_ui"
            );
            
            log.debug("A/B Test - pivot: {}, experiment: {}, variable: {} => {}",
                pivotValue, experimentName, variableName, 
                result ? "TREATMENT" : "CONTROL");
            
            return result;
            
        } catch (Exception e) {
            log.error("Error checking A/B test for pivot: {}, experiment: {}, variable: {}",
                pivotValue, experimentName, variableName, e);
            return false;  // Default to control on error
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
```

### **3. SPI Registration Files**

**CRITICAL:** Create files in `src/main/resources/META-INF/services/` to register your implementations:

#### **File 1:** `src/main/resources/META-INF/services/com.flipkart.drift.sdk.spi.auth.TokenProvider`

```
com.yourorg.drift.worker.yourorg.auth.YourOrgTokenProvider
```

#### **File 2:** `src/main/resources/META-INF/services/com.flipkart.drift.sdk.spi.ab.ABTestingProvider`

```
com.yourorg.drift.worker.yourorg.ab.YourOrgABTestingProvider
```

**Rules:**
- ✅ File name MUST exactly match the interface's fully qualified name
- ✅ Content MUST be the fully qualified class name of your implementation
- ✅ One implementation per line (if you have multiple)
- ✅ No comments, no extra whitespace
- ❌ File extension should be **nothing** (no `.txt`, no `.properties`)

### **4. How SPI Discovery Works**

```
┌─────────────────────────────────────────────────────────┐
│ 1. Worker starts                                        │
│    └─> Factories load via static initializers          │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 2. TokenProviderFactory static block runs               │
│    ServiceLoader.load(TokenProvider.class)              │
│    └─> Scans classpath for META-INF/services files     │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 3. Finds two locations:                                 │
│    worker-yourorg.jar!/META-INF/services/...            │ ← First!
│      └─> YourOrgTokenProvider                           │
│    worker.jar!/META-INF/services/...                    │ ← Second
│      └─> NoOpTokenProvider                              │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 4. iterator.next() returns FIRST provider found         │
│    └─> YourOrgTokenProvider (from extension)           │
│                                                          │
│ 5. Factory logs:                                        │
│    "Discovered YourOrgTokenProvider via SPI"            │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 6. Worker bootstrap calls provider.init()               │
│    └─> Your custom initialization logic runs           │
└─────────────────────────────────────────────────────────┘
```

**Key Point:** Classpath order matters! Your extension JAR must be **before** base worker JAR.

---

## Local Testing in IntelliJ

### **Step 1: Build Your Extension JAR**

```bash
cd worker-yourorg
mvn clean package -DskipTests
```

This creates: `target/worker-yourorg-1.0.0.jar`

### **Step 2: Configure IntelliJ Run Configuration**

#### **A. Open Run Configuration:**
- `Run` → `Edit Configurations` → Select (or create) `WorkerApplication`

#### **B. Basic Settings:**
```
Name: Drift Worker (with YourOrg Extension)
Main class: com.flipkart.drift.worker.bootstrap.WorkerApplication
Module: worker
```

#### **C. Program Arguments:**
```
server src/main/resources/config/configuration.yaml
```

#### **D. VM Options:**
```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
-Dfile.encoding=UTF-8
```

#### **E. Environment Variables:**
```
# Redis Configuration
REDIS_PASSWORD=your-redis-password
REDIS_MASTER=redis-master-hostname
REDIS_SENTINELS=redis-sentinel1:26379,redis-sentinel2:26379
REDIS_PREFIX=drift-local
REDIS_DATABASE=0

# Config File Paths (relative to worker module)
ENUM_STORE_BUCKET=src/main/resources/config/lookup.properties
HBASE_CONFIG_BUCKET=src/main/resources/config/hbase.properties
AB_CONFIG_BUCKET=src/main/resources/config/ab.properties
AUTH_PATH=src/main/resources/config/auth.properties

# Temporal Configuration
TEMPORAL_FRONTEND=localhost:7233
TEMPORAL_TASK_QUEUE=DRIFT_QUEUE

# HBase/Hadoop Configuration
HADOOP_USERNAME=your-hadoop-user
HADOOP_LOGIN_USER=your-hadoop-user
ZOOKEEPER_QUORUM_HOT=zk1:2181,zk2:2181,zk3:2181

# YOUR ORG-SPECIFIC CONFIG (for your providers)
AUTH_SERVICE_URL=https://auth.yourorg.com
AUTH_CLIENT_ID=drift-worker
AUTH_CLIENT_SECRET=your-secret

EXPERIMENT_SERVICE_URL=https://experiments.yourorg.com
EXPERIMENT_API_KEY=your-api-key
```

#### **F. Modify Classpath (CRITICAL!):**

1. Click **"Modify options"** → Enable **"Modify classpath"**
2. Click **"Modify classpath"** button
3. Click **"+"** → **"JARs or directories"**
4. Add: `/path/to/worker-yourorg/target/worker-yourorg-1.0.0.jar`
5. **IMPORTANT:** Use arrow buttons to move this JAR to the **TOP** of the list
   ```
   ✅ Correct order:
   1. worker-yourorg-1.0.0.jar   ← Your extension FIRST
   2. worker module classes       ← Base worker SECOND
   3. All other dependencies
   
   ❌ Wrong order:
   1. worker module classes       ← Base worker first
   2. worker-yourorg-1.0.0.jar    ← Extension second (won't work!)
   ```

#### **G. Before Launch Actions:**

Add a Maven goal to auto-build your extension:
1. Click **"+"** in "Before launch" section
2. Select **"Run Maven Goal"**
3. Command line: `clean package -DskipTests -f worker-yourorg/pom.xml`

### **Step 3: Run and Verify**

Start the worker and check logs for:

```
INFO  TokenProviderFactory: Discovered com.yourorg.drift.worker.yourorg.auth.YourOrgTokenProvider via SPI (not yet initialized)
INFO  ABTestingProviderFactory: Discovered com.yourorg.drift.worker.yourorg.ab.YourOrgABTestingProvider via SPI (not yet initialized)
...
INFO  YourOrgTokenProvider: Initializing YourOrg AuthClient with URL: https://auth.yourorg.com
INFO  YourOrgTokenProvider: YourOrgTokenProvider initialized successfully
INFO  YourOrgABTestingProvider: Initializing YourOrg ExperimentClient with URL: https://experiments.yourorg.com
INFO  YourOrgABTestingProvider: YourOrgABTestingProvider initialized successfully
```

✅ **Success Indicators:**
- See `Discovered com.yourorg...` (not `NoOpTokenProvider`)
- No `ClassNotFoundException` or `NoSuchMethodError`
- Worker starts and accepts Temporal tasks

❌ **Failure Indicators:**
- `Discovered NoOpTokenProvider` → Classpath order wrong
- `NoClassDefFoundError` → Missing dependency or need to exclude common lib
- `NoSuchMethodError` → Version conflict, may need package relocation

---

## Docker Packaging

### **Dockerfile Structure**

```dockerfile
# Base image is the public drift-worker
FROM <your-registry>/drift-worker:latest

# Create extensions directory
RUN mkdir -p /usr/share/drift-worker/extensions

# Copy your custom extension JAR
# This JAR contains:
# - Your SPI implementations (TokenProvider, ABTestingProvider)
# - Your organization's libraries (shaded/excluded as needed)
# - META-INF/services registration files
COPY target/worker-yourorg-*.jar /usr/share/drift-worker/extensions/worker-yourorg.jar

# (Optional) Copy custom Groovy scripts required by your node specs
COPY src/main/resources/scripts/* /src/main/resources/scripts/

# The base image already has ENTRYPOINT configured
# The entrypoint.sh will:
# 1. Add /usr/share/drift-worker/extensions/* to classpath FIRST
# 2. Add /usr/share/drift-worker/service/worker.jar SECOND
# 3. Start the worker: java -cp $CLASSPATH com.flipkart.drift.worker.bootstrap.WorkerApplication
```

### **How the Entrypoint Works**

The base worker's `entrypoint.sh` builds the classpath in this order:

```bash
# Simplified view of entrypoint.sh in base worker
CLASSPATH=""

# 1. Extensions FIRST (your custom JAR)
if [ -d "/usr/share/drift-worker/extensions" ]; then
    CLASSPATH="/usr/share/drift-worker/extensions/*"
fi

# 2. Base worker JAR SECOND
CLASSPATH="$CLASSPATH:/usr/share/drift-worker/service/worker.jar"

# 3. Start worker
exec java $JVM_OPTS \
  -cp "$CLASSPATH" \
  com.flipkart.drift.worker.bootstrap.WorkerApplication \
  server /usr/share/drift-worker/config/config.yaml
```

**Why This Order Matters:**

```
ServiceLoader scans classpath LEFT-TO-RIGHT:

Classpath: /extensions/worker-yourorg.jar:/service/worker.jar
           ↑                              ↑
           Checked FIRST                  Checked SECOND
           (Your implementation)          (NoOp fallback)
```

When `ServiceLoader.load(TokenProvider.class)` runs:
1. Finds `META-INF/services/...TokenProvider` in `worker-yourorg.jar` → Returns `YourOrgTokenProvider` ✅
2. Also finds one in `worker.jar` but **iterator.next()** already returned the first match

### **Building the Docker Image**

```bash
# 1. Build extension JAR
cd worker-yourorg
mvn clean package -DskipTests

# 2. Build Docker image
docker build -t <your-registry>/drift-worker-yourorg:1.0.0 .

# 3. Tag for latest
docker tag <your-registry>/drift-worker-yourorg:1.0.0 \
           <your-registry>/drift-worker-yourorg:latest

# 4. Push to registry
docker push <your-registry>/drift-worker-yourorg:1.0.0
docker push <your-registry>/drift-worker-yourorg:latest
```
---

## Deployment

### **Using Helm Charts**

Once your Docker image is built and pushed, deploy using the Helm charts provided in the `package/helm-chart/worker` directory.

**Reference:** Refer `docs` for complete Helm chart documentation.



### **Expected Logs on Successful Deployment:**

```
INFO  TokenProviderFactory: Discovered com.yourorg.drift.worker.yourorg.auth.YourOrgTokenProvider via SPI (not yet initialized)
INFO  ABTestingProviderFactory: Discovered com.yourorg.drift.worker.yourorg.ab.YourOrgABTestingProvider via SPI (not yet initialized)
INFO  YourOrgTokenProvider: Initializing YourOrg AuthClient with URL: https://auth.yourorg.com
INFO  YourOrgTokenProvider: YourOrgTokenProvider initialized successfully
INFO  YourOrgABTestingProvider: YourOrgABTestingProvider initialized successfully
INFO  TemporalWorkerManaged: Starting Temporal worker...
INFO  WorkerFactory: Worker started for task queue: DRIFT_QUEUE
```

---
**Remember:** Your extension runs in the same JVM as the base worker. Keep it lightweight, handle errors gracefully, and log verbosely for debugging!
