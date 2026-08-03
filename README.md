# ScanRest

Auto API testing library for Spring Boot projects. Scans controllers, generates YAML test specs, and executes tests automatically.

## Features

- **Endpoint Scanner** - Auto-discovers all REST endpoints by scanning `@RestController`, `@GetMapping`, `@PostMapping`, etc.
- **YAML Generator** - Generates a skeleton YAML test file from scanned endpoints
- **Simple + Detailed YAML Formats** - One-liner tests for simple cases, full control when needed
- **Profiles** - Dev/staging/prod with variable substitution (like Spring profiles)
- **Variable Interpolation** - `{{variable}}` syntax with global + saved values
- **JSONPath Assertions** - `$.field: NOT_NULL`, `$.name: "John"`, etc.
- **Matchers** - `NOT_NULL`, `CONTAINS(...)`, `STARTS_WITH(...)`, `MATCHES(...)`, `GT(n)`, `SIZE(n)`, etc.
- **Test Chaining** - `dependsOn` + `save` to chain tests and pass data between them
- **Parameterized Tests** - Bulk test data with different inputs/expectations
- **JSON Schema Validation** - Validate response structure against JSON Schema files
- **Auth Override** - Skip auth headers per test (`auth: NONE`)
- **Dual Execution Modes** - Live HTTP (RestAssured) or Embedded Spring (MockMvc)
- **Spring Boot Starter** - Auto-configuration with `application.properties` support
- **Console & File Reports** - Color-coded console output + exportable report files

## Usage

ScanRest can be used in two ways:
1. **As a Spring Boot Starter** - Add as a dependency and configure via `application.properties`
2. **As a Standalone CLI** - Run the JAR directly against any Spring Boot project

---

## Option 1: Spring Boot Starter (Recommended)

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.yubraj.test</groupId>
    <artifactId>scanrest</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### 2. Configure in `application.properties`

```properties
# Enable ScanRest (default: true)
scanrest.enabled=true

# Path to the YAML test specification file
scanrest.file=scanrest-tests.yml

# Execution mode: LIVE or EMBEDDED
scanrest.mode=LIVE

# Active profile (overrides YAML config)
scanrest.profile=dev

# Base URL override (for live mode)
scanrest.base-url=http://localhost:8080

# Auto-scan controllers and generate YAML if file doesn't exist
scanrest.auto-generate=false

# Fail the build when tests fail
scanrest.fail-on-error=true

# Write test report to file
scanrest.report-path=target/scanrest-report.txt

# Run tests on application startup
scanrest.run-on-startup=false
```

Or in `application.yml`:

```yaml
scanrest:
  enabled: true
  file: scanrest-tests.yml
  mode: LIVE
  profile: dev
  base-url: http://localhost:8080
  auto-generate: false
  fail-on-error: true
  report-path: target/scanrest-report.txt
  run-on-startup: false
```

### 3. Write a JUnit Test

**Option A: Use `@EnableScanRest` annotation**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableScanRest
class ApiTests {

    @Autowired
    private ScanRestTestRunner scanRestRunner;

    @Test
    void runAllApiTests() {
        List<TestResult> results = scanRestRunner.runTests();
        assertThat(results).allMatch(TestResult::isPassed);
    }
}
```

**Option B: Auto-run on startup (no test class needed)**

```properties
scanrest.enabled=true
scanrest.run-on-startup=true
scanrest.fail-on-error=true
```

Tests run automatically when the Spring context starts during `mvn test`.

### All Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scanrest.enabled` | `boolean` | `true` | Enable/disable ScanRest entirely |
| `scanrest.file` | `String` | `scanrest-tests.yml` | Path to YAML test file |
| `scanrest.mode` | `LIVE\|EMBEDDED` | `EMBEDDED` | Execution mode |
| `scanrest.profile` | `String` | - | Active profile (overrides YAML) |
| `scanrest.base-url` | `String` | - | Base URL override for live mode |
| `scanrest.auto-generate` | `boolean` | `false` | Auto-generate YAML from scanned endpoints |
| `scanrest.fail-on-error` | `boolean` | `true` | Fail build on test failure |
| `scanrest.report-path` | `String` | - | Path to write report file |
| `scanrest.run-on-startup` | `boolean` | `false` | Run tests on app startup |

---

## Option 2: Standalone CLI

### 1. Build the CLI

### 1. Build the CLI

```bash
mvn clean package -DskipTests
```

Produces a fat JAR at `target/scanrest-1.0-SNAPSHOT.jar`.

### 2. Scan Endpoints

```bash
java -jar scanrest.jar scan --classpath /path/to/your-spring-app/target/classes
```

### 3. Generate YAML Test File

```bash
java -jar scanrest.jar generate \
  --classpath /path/to/your-spring-app/target/classes \
  --output scanrest-tests.yml \
  --name "My API Tests" \
  --base-url http://localhost:8080
```

### 4. Edit the YAML and Run Tests

```bash
# Live mode (against a running server):
java -jar scanrest.jar test --file scanrest-tests.yml --mode live

# With a specific profile:
java -jar scanrest.jar test --file scanrest-tests.yml --mode live --profile staging

# Embedded mode (no server needed):
java -jar scanrest.jar test --file scanrest-tests.yml --mode embedded --app-class com.example.App

# With report file:
java -jar scanrest.jar test --file scanrest-tests.yml --report test-report.txt
```

## YAML Format

### Config Block

```yaml
config:
  name: "User Service API Tests"
  baseUrl: "http://localhost:8080"
  activeProfile: dev

  profiles:
    dev:
      baseUrl: "http://localhost:8080"
      token: "dev-token-123"
    staging:
      baseUrl: "https://staging.api.com"
      token: "staging-token-456"

  globalHeaders:
    Content-Type: "application/json"
    Authorization: "Bearer {{token}}"

  variables:
    userId: "101"
    adminEmail: "admin@test.com"
```

### Simple Test Format (one-liner)

```yaml
tests:
  # Just check status code
  - GET /api/users:
      expect: 200

  # With JSONPath body assertions
  - GET /api/users/{{userId}}:
      expect: 200
      body:
        $.id: 101
        $.email: NOT_NULL
```

### Detailed Test Format

```yaml
tests:
  - name: "Create a new user"
    path: /api/users
    method: POST
    request:
      headers:
        X-Custom: "value"
      queryParams:
        notify: "true"
      body:
        name: "John Doe"
        email: "john@example.com"
    expect:
      status: 201
      body:
        $.id: NOT_NULL
        $.name: "John Doe"
      headers:
        Location: CONTAINS("/api/users/")
      schema: "schemas/user.json"
      save:
        createdUserId: "$.id"
```

### Test Chaining

```yaml
tests:
  - name: "Create user"
    path: /api/users
    method: POST
    request:
      body: { name: "John" }
    expect:
      status: 201
      save:
        newId: "$.id"

  - name: "Verify user"
    path: /api/users/{{newId}}
    method: GET
    dependsOn: "Create user"
    expect:
      status: 200
```

### Parameterized Tests

```yaml
tests:
  - name: "User lookup"
    path: /api/users/{{id}}
    method: GET
    parameterized:
      - { id: "1",   expect: 200 }
      - { id: "2",   expect: 200 }
      - { id: "999", expect: 404 }
      - { id: "abc", expect: 400 }
```

### Auth Override

```yaml
tests:
  - name: "Reject unauthenticated"
    path: /api/admin/users
    method: GET
    auth: NONE              # Skips global Authorization header
    expect:
      status: 401
```

## Assertion Matchers

| Matcher | Description | Example |
|---------|-------------|---------|
| `NOT_NULL` | Value exists and is not null | `$.id: NOT_NULL` |
| `NULL` | Value is null | `$.deleted: NULL` |
| `NOT_EMPTY` | String/array is not empty | `$.items: NOT_EMPTY` |
| `CONTAINS("x")` | String contains substring | `$.email: CONTAINS("@")` |
| `STARTS_WITH("x")` | String starts with prefix | `$.url: STARTS_WITH("https")` |
| `ENDS_WITH("x")` | String ends with suffix | `$.file: ENDS_WITH(".pdf")` |
| `MATCHES("regex")` | Matches regex pattern | `$.phone: MATCHES("\\d{10}")` |
| `GT(n)` | Greater than | `$.age: GT(18)` |
| `LT(n)` | Less than | `$.price: LT(100)` |
| `GTE(n)` | Greater or equal | `$.count: GTE(1)` |
| `LTE(n)` | Less or equal | `$.score: LTE(100)` |
| `SIZE(n)` | Array/string length | `$.items: SIZE(5)` |
| `"value"` | Exact equality | `$.name: "John"` |

## Project Structure

```
src/main/java/com/yubraj/test/scanrest/
├── autoconfigure/                   # Spring Boot auto-configuration
│   ├── ScanRestAutoConfiguration.java   # Auto-config (beans, conditional)
│   ├── ScanRestProperties.java          # @ConfigurationProperties
│   ├── ScanRestTestRunner.java          # Test runner (startup + programmatic)
│   ├── ScanRestTestFailureException.java
│   └── EnableScanRest.java              # @EnableScanRest annotation
├── cli/                             # Picocli CLI commands
│   ├── ScanRestCli.java             # Main entry point
│   ├── ScanCommand.java             # 'scan' command
│   ├── GenerateCommand.java         # 'generate' command
│   └── TestCommand.java             # 'test' command
├── config/
│   └── YamlParser.java              # YAML parser (simple + detailed formats)
├── engine/
│   ├── AssertionEngine.java         # JSONPath + matcher validation engine
│   ├── MatcherEngine.java           # Matcher expression evaluator
│   └── VariableResolver.java        # {{variable}} interpolation
├── executor/
│   ├── TestExecutor.java            # Executor interface
│   ├── LiveHttpExecutor.java        # RestAssured (live server)
│   └── EmbeddedSpringExecutor.java  # MockMvc (embedded)
├── generator/
│   └── YamlGenerator.java           # Skeleton YAML generator
├── model/
│   ├── TestSuiteSpec.java           # Root spec (config + tests)
│   ├── ScanRestConfig.java          # Config block (profiles, vars, headers)
│   ├── TestSpec.java                # Single test definition
│   ├── RequestSpec.java             # Request (headers, body, params)
│   ├── ExpectSpec.java              # Expected response (status, body, save)
│   ├── HttpMethod.java              # HTTP method enum
│   ├── TestResult.java              # Execution result
│   └── ScannedEndpoint.java         # Scanned endpoint info
├── report/
│   └── TestReporter.java            # Console & file reporting
└── scanner/
    └── EndpointScanner.java         # Spring annotation scanner
```

## Requirements

- Java 21+
- Maven 3.8+
- Target Spring Boot project must be compiled (`mvn compile`)

## License

MIT
