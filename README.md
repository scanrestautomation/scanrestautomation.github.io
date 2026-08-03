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
- **Three Execution Modes** - Live HTTP (RestAssured), MockMvc (existing Spring context), or auto-mock
- **Spring Boot Starter** - Auto-configuration with `application.properties` support
- **Test Context Aware** - Respects `@MockBean`, `@WebMvcTest`, `@ActiveProfiles`, test slices
- **Auto-Mock Services** - `scanrest.mock-services=true` replaces `@Service` beans with Mockito mocks
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

# Execution mode: LIVE | MOCKMVC | EMBEDDED (deprecated alias for MOCKMVC)
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

# Auto-mock all @Service beans (for MOCKMVC mode)
scanrest.mock-services=false
```

### 3. Write a Test Class

Use `@TestFactory` to generate one JUnit test per YAML test case. Each test appears individually in Maven Surefire, IntelliJ, and CI dashboards:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableScanRest
class ApiTests {

    @Autowired
    private ScanRestTestRunner scanRestRunner;

    @TestFactory
    Collection<DynamicTest> apiTests() {
        return scanRestRunner.toDynamicTests();
    }
}
```

This produces:
```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
  ✓ GET /api/users
  ✓ Create user
  ✓ Get created user by ID
  ✓ Update user
  ...
```

Each YAML test case is a first-class JUnit test with its own pass/fail/skip status.

### 4. Choose a Test Strategy

ScanRest supports multiple test strategies. Pick the one that fits your needs:

#### Strategy A: Full Integration Test (LIVE mode)

Tests run against a real HTTP server with the full application stack.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableScanRest
class FullIntegrationTests {

    @Autowired
    private ScanRestTestRunner scanRestRunner;

    @TestFactory
    Collection<DynamicTest> apiTests() {
        return scanRestRunner.toDynamicTests();
    }
}
```

```properties
scanrest.mode=LIVE
```

The server port is auto-detected from `local.server.port`.

#### Strategy B: MockMvc with Mocked Services

Tests run via MockMvc against the existing Spring context. You control which beans are mocked.

```java
@SpringBootTest
@EnableScanRest
class MockedServiceTests {

    @MockBean
    private UserService userService;  // You control the mock

    @Autowired
    private ScanRestTestRunner scanRestRunner;

    @BeforeEach
    void setup() {
        User testUser = new User(1L, "John", "Doe", "john@example.com", "1234567890", "ACTIVE");
        when(userService.getAllUsers()).thenReturn(List.of(testUser));
        when(userService.getUserById(1L)).thenReturn(Optional.of(testUser));
        when(userService.createUser(any())).thenReturn(testUser);
    }

    @TestFactory
    Collection<DynamicTest> apiTests() {
        return scanRestRunner.toDynamicTests();
    }
}
```

```properties
scanrest.mode=MOCKMVC
```

#### Strategy C: Controller-Only Test Slice

Only the controller layer is loaded. Services must be `@MockBean`-ed.

```java
@WebMvcTest(UserController.class)
@EnableScanRest
class ControllerSliceTests {

    @MockBean
    private UserService userService;

    @Autowired
    private ScanRestTestRunner scanRestRunner;

    @BeforeEach
    void setup() {
        // Set up mock returns
    }

    @TestFactory
    Collection<DynamicTest> controllerTests() {
        return scanRestRunner.toDynamicTests();
    }
}
```

```properties
scanrest.mode=MOCKMVC
```

#### Strategy D: Auto-Mock Services (Convenience)

ScanRest automatically replaces all `@Service` beans with Mockito mocks. Quick and easy, but mock methods return default values (null, 0, empty collections).

```properties
scanrest.mode=MOCKMVC
scanrest.mock-services=true
scanrest.run-on-startup=true
```

Best for verifying controllers don't throw exceptions and return correct status codes, without needing real service logic.

#### Strategy E: In-Memory Database (Test Profile)

Use Spring's `@ActiveProfiles` to swap the real database for an in-memory one.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableScanRest
class InMemoryDbTests {

    @Autowired
    private ScanRestTestRunner scanRestRunner;

    @TestFactory
    Collection<DynamicTest> apiTests() {
        return scanRestRunner.toDynamicTests();
    }
}
```

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop

scanrest:
  mode: LIVE
  run-on-startup: true
```

#### Strategy F: Auto-run on Startup (No @TestFactory)

Uses `run-on-startup` to execute all tests as a single batch on context startup.
Results appear as 1 test in JUnit (not individual). Use `@TestFactory` (strategies A-E) for individual test visibility.

```properties
scanrest.enabled=true
scanrest.mode=LIVE
scanrest.run-on-startup=true
scanrest.fail-on-error=true
```

#### Strategy G: Batch with `runTests()` (Backward Compatible)

If you prefer a single test method wrapping all YAML tests:

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

This shows `Tests run: 1` in JUnit. Use `@TestFactory` instead for individual test visibility.

### All Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scanrest.enabled` | `boolean` | `true` | Enable/disable ScanRest entirely |
| `scanrest.file` | `String` | `scanrest-tests.yml` | Path to YAML test file |
| `scanrest.mode` | `LIVE\|MOCKMVC\|EMBEDDED` | `LIVE` | Execution mode |
| `scanrest.profile` | `String` | - | Active profile (overrides YAML) |
| `scanrest.base-url` | `String` | - | Base URL override for live mode |
| `scanrest.auto-generate` | `boolean` | `false` | Auto-generate YAML from scanned endpoints |
| `scanrest.fail-on-error` | `boolean` | `true` | Fail build on test failure |
| `scanrest.report-path` | `String` | - | Path to write report file |
| `scanrest.run-on-startup` | `boolean` | `false` | Run tests on app startup |
| `scanrest.mock-services` | `boolean` | `false` | Auto-mock `@Service` beans with Mockito |

### Execution Modes

| Mode | Server | Context | Best For |
|------|--------|---------|----------|
| `LIVE` | Real HTTP (RestAssured) | Full app + real server | Integration tests, E2E |
| `MOCKMVC` | MockMvc (no HTTP) | Existing Spring context | Unit/controller tests, @MockBean, @WebMvcTest |
| `EMBEDDED` | *(deprecated)* | Alias for MOCKMVC | Use MOCKMVC instead |

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
│   ├── ScanRestMockConfiguration.java   # Auto-mock @Service beans
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
│   ├── MockMvcExecutor.java         # MockMvc (existing Spring context)
│   └── EmbeddedSpringExecutor.java  # MockMvc (boots own context, for CLI)
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
