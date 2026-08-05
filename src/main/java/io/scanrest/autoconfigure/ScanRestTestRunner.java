package io.scanrest.autoconfigure;

import io.scanrest.config.YamlParser;
import io.scanrest.engine.VariableResolver;
import io.scanrest.executor.LiveHttpExecutor;
import io.scanrest.executor.MockMvcExecutor;
import io.scanrest.executor.TestExecutor;
import io.scanrest.generator.YamlGenerator;
import io.scanrest.model.*;
import io.scanrest.report.TestReporter;
import io.scanrest.scanner.EndpointScanner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Executes ScanRest tests within the Spring Boot lifecycle.
 *
 * <p>Supports two execution modes:</p>
 * <ul>
 *   <li><b>LIVE</b>: HTTP requests via RestAssured against a running server.
 *       Auto-detects the server port from {@code local.server.port}.</li>
 *   <li><b>MOCKMVC</b>: Uses Spring MockMvc from the existing application context.
 *       Respects {@code @MockBean}, {@code @TestConfiguration}, {@code @ActiveProfiles},
 *       and {@code @WebMvcTest} test slices.</li>
 * </ul>
 *
 * <p>Can run tests:</p>
 * <ul>
 *   <li>Automatically on startup ({@code scanrest.run-on-startup=true})</li>
 *   <li>Programmatically by injecting this bean and calling {@link #runTests()}</li>
 *   <li>As individual JUnit tests via {@link #toDynamicTests()} with {@code @TestFactory}</li>
 *   <li>From a JUnit test via {@link EnableScanRest} annotation</li>
 * </ul>
 */
public class ScanRestTestRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanRestTestRunner.class);

    private final ScanRestProperties properties;
    private final YamlParser yamlParser;
    private final EndpointScanner endpointScanner;
    private final YamlGenerator yamlGenerator;
    private final TestReporter testReporter;
    private final Environment environment;
    private final ApplicationContext applicationContext;
    private final MockMvc injectedMockMvc; // may be null (only available in @WebMvcTest or manually configured)

    public ScanRestTestRunner(ScanRestProperties properties,
                               YamlParser yamlParser,
                               EndpointScanner endpointScanner,
                               YamlGenerator yamlGenerator,
                               TestReporter testReporter,
                               Environment environment,
                               ApplicationContext applicationContext,
                               MockMvc mockMvc) {
        this.properties = properties;
        this.yamlParser = yamlParser;
        this.endpointScanner = endpointScanner;
        this.yamlGenerator = yamlGenerator;
        this.testReporter = testReporter;
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.injectedMockMvc = mockMvc;
    }

    /**
     * Logs ScanRest status and optionally runs tests on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled()) {
            return;
        }

        Path yamlPath = resolveYamlPath();
        String modeDisplay = properties.isMockMvcMode() ? "MOCKMVC" : "LIVE";

        log.info("==================================================");
        log.info("  ScanRest is ACTIVE");
        log.info("==================================================");
        log.info("  Test file     : {}", properties.getFile());
        log.info("  Mode          : {}", modeDisplay);
        if (!properties.isMockMvcMode()) {
            log.info("  Base URL      : {}", resolveBaseUrl());
        }
        log.info("  Profile       : {}", properties.getProfile() != null ? properties.getProfile() : "(default)");
        log.info("  Run on startup: {}", properties.isRunOnStartup());
        log.info("  Fail on error : {}", properties.isFailOnError());
        if (properties.isMockServices()) {
            log.info("  Mock services : ENABLED");
        }

        if (yamlPath != null) {
            log.info("  Test file     : FOUND ({})", yamlPath.toAbsolutePath());
        } else {
            log.warn("  Test file     : NOT FOUND");
            log.info("  Place '{}' in src/test/resources/", properties.getFile());
            if (properties.isAutoGenerate()) {
                log.info("  Auto-generate : enabled - will generate on first run");
            }
        }
        log.info("==================================================");

        if (!properties.isRunOnStartup()) {
            log.info("ScanRest: Waiting for manual trigger. Inject ScanRestTestRunner and call runTests()");
            log.info("  Or set scanrest.run-on-startup=true to run automatically.");
            return;
        }

        log.info("ScanRest: Running tests on startup...");
        List<TestResult> results = runTests();
        if (!testReporter.allPassed(results) && properties.isFailOnError()) {
            throw new ScanRestTestFailureException(
                    "ScanRest tests failed! %d of %d tests failed.".formatted(
                            results.stream().filter(r -> !r.isPassed()).count(),
                            results.size()));
        }
    }

    /**
     * Run all tests defined in the YAML file as a batch.
     * Results are printed to console and optionally written to a report file.
     *
     * @return list of test results
     */
    public List<TestResult> runTests() {
        try {
            TestSuiteSpec suite = loadSuite();
            if (suite == null) return List.of();

            VariableResolver resolver = new VariableResolver(suite.getConfig().resolveVariables());
            TestExecutor executor = createExecutor(resolveYamlPath());

            List<TestResult> results = executor.execute(suite, resolver);

            testReporter.printConsoleReport(results, System.out);
            if (properties.getReportPath() != null) {
                testReporter.writeFileReport(results, Path.of(properties.getReportPath()));
            }

            return results;

        } catch (Exception e) {
            log.error("ScanRest: Error running tests: {}", e.getMessage(), e);
            TestResult errorResult = new TestResult();
            errorResult.setStatus(TestResult.Status.ERROR);
            errorResult.setErrorMessage(e.getMessage());
            errorResult.setDuration(Duration.ZERO);
            return List.of(errorResult);
        }
    }

    /**
     * Generate JUnit 5 {@link DynamicTest} instances — one per YAML test case.
     * Each test case appears as an individual test in JUnit reports, Maven Surefire,
     * and IDE test runners.
     *
     * <p>Usage:</p>
     * <pre>
     * &#064;SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
     * &#064;EnableScanRest
     * class ApiTests {
     *     &#064;Autowired
     *     private ScanRestTestRunner scanRestRunner;
     *
     *     &#064;TestFactory
     *     Collection&lt;DynamicTest&gt; apiTests() {
     *         return scanRestRunner.toDynamicTests();
     *     }
     * }
     * </pre>
     *
     * <p>Supports:</p>
     * <ul>
     *   <li>Variable chaining ({@code save} / {@code dependsOn})</li>
     *   <li>Parameterized tests (each variant is a separate DynamicTest)</li>
     *   <li>Dependent tests are skipped (via JUnit Assumptions) when prerequisites fail</li>
     * </ul>
     *
     * @return collection of DynamicTest instances, one per YAML test case
     */
    public Collection<DynamicTest> toDynamicTests() {
        TestSuiteSpec suite = loadSuite();
        if (suite == null) {
            return List.of();
        }

        VariableResolver resolver = new VariableResolver(suite.getConfig().resolveVariables());
        Map<String, String> globalHeaders = resolver.resolveMap(suite.getConfig().getGlobalHeaders());
        TestExecutor executor = createExecutor(resolveYamlPath());

        // For LIVE mode, set the baseUrl on the executor
        if (executor instanceof LiveHttpExecutor liveExecutor) {
            liveExecutor.setBaseUrl(suite.getConfig().resolveBaseUrl());
        }

        // Shared state for test chaining (dependsOn / save)
        Map<String, TestResult> completedTests = new LinkedHashMap<>();
        List<DynamicTest> dynamicTests = new ArrayList<>();

        for (TestSpec test : suite.getTests()) {
            if (test.isParameterized()) {
                // Expand parameterized tests into individual DynamicTests
                for (int i = 0; i < test.getParameterized().size(); i++) {
                    Map<String, Object> params = test.getParameterized().get(i);
                    final int index = i;
                    String name = "%s [%d] %s".formatted(test.displayName(), i + 1, params);

                    dynamicTests.add(DynamicTest.dynamicTest(name, () -> {
                        VariableResolver paramResolver = resolver.copy();
                        ExpectSpec paramExpect = null;
                        for (var entry : params.entrySet()) {
                            if ("expect".equals(entry.getKey())) {
                                paramExpect = buildExpectFromParam(entry.getValue());
                            } else {
                                paramResolver.set(entry.getKey(), String.valueOf(entry.getValue()));
                            }
                        }

                        TestResult result = executor.executeSingle(test, globalHeaders, paramResolver,
                                paramExpect != null ? paramExpect : test.getExpect(), name);
                        completedTests.put(name, result);
                        assertTestResult(result);
                    }));
                }
            } else {
                String name = test.displayName();

                dynamicTests.add(DynamicTest.dynamicTest(name, () -> {
                    // Check dependency
                    if (test.getDependsOn() != null) {
                        TestResult dep = completedTests.get(test.getDependsOn());
                        Assumptions.assumeTrue(dep != null && dep.isPassed(),
                                "Skipped: dependency '%s' not met".formatted(test.getDependsOn()));
                    }

                    TestResult result = executor.executeSingle(test, globalHeaders, resolver,
                            test.getExpect(), name);
                    completedTests.put(name, result);
                    assertTestResult(result);
                }));
            }
        }

        return dynamicTests;
    }

    /**
     * Assert a TestResult, failing the JUnit test if it didn't pass.
     */
    private void assertTestResult(TestResult result) {
        if (result.isPassed()) return;

        if (result.getStatus() == TestResult.Status.ERROR) {
            fail("Test error: " + result.getErrorMessage());
        }

        if (result.getStatus() == TestResult.Status.FAILED) {
            String failureDetails = String.join("\n  ", result.getFailures());
            fail("Assertion failures:\n  " + failureDetails);
        }
    }

    /**
     * Load and configure the test suite from YAML.
     */
    private TestSuiteSpec loadSuite() {
        try {
            Path yamlPath = resolveYamlPath();

            // Auto-generate YAML if enabled and file doesn't exist
            if (yamlPath == null && properties.isAutoGenerate()) {
                Path generateTo = Path.of("src/test/resources", properties.getFile());
                log.info("ScanRest: Test file not found. Auto-generating: {}", generateTo);
                autoGenerateYaml(generateTo);
                yamlPath = generateTo;
            }

            if (yamlPath == null || !Files.exists(yamlPath)) {
                log.warn("ScanRest: Test file '{}' not found in src/test/resources/. Skipping tests.", properties.getFile());
                return null;
            }

            TestSuiteSpec suite = yamlParser.parse(yamlPath);

            // Override profile from properties
            if (properties.getProfile() != null) {
                suite.getConfig().setActiveProfile(properties.getProfile());
            }

            // Override baseUrl for LIVE mode
            if (!properties.isMockMvcMode()) {
                String effectiveBaseUrl = resolveBaseUrl();
                suite.getConfig().setBaseUrl(effectiveBaseUrl);
            }

            return suite;

        } catch (Exception e) {
            log.error("ScanRest: Error loading test suite: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build an ExpectSpec from a parameterized test's 'expect' value.
     */
    @SuppressWarnings("unchecked")
    private ExpectSpec buildExpectFromParam(Object value) {
        ExpectSpec expect = new ExpectSpec();
        if (value instanceof Integer status) {
            expect.setStatus(status);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> expectMap = (Map<String, Object>) map;
            if (expectMap.containsKey("status")) {
                expect.setStatus(((Number) expectMap.get("status")).intValue());
            }
        }
        return expect;
    }

    /**
     * Resolve the effective base URL for LIVE mode.
     * Priority:
     * 1. {@code scanrest.base-url} property (explicit override)
     * 2. Auto-detect from Spring's {@code local.server.port} (for @SpringBootTest RANDOM_PORT)
     * 3. Auto-detect from Spring's {@code server.port} property
     * 4. Default: http://localhost:8080
     */
    private String resolveBaseUrl() {
        // 1. Explicit property override
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            return properties.getBaseUrl();
        }

        // 2. Auto-detect from local.server.port (set by @SpringBootTest with RANDOM_PORT or DEFINED_PORT)
        String localPort = environment.getProperty("local.server.port");
        if (localPort != null) {
            log.debug("ScanRest: Auto-detected local.server.port={}", localPort);
            return "http://localhost:" + localPort;
        }

        // 3. Auto-detect from server.port
        String serverPort = environment.getProperty("server.port");
        if (serverPort != null && !"0".equals(serverPort)) {
            return "http://localhost:" + serverPort;
        }

        // 4. Default
        return "http://localhost:8080";
    }

    /**
     * Resolve the YAML test file path.
     * Priority:
     * 1. Classpath (src/test/resources/ at runtime) via classloader
     * 2. src/test/resources/ directly (for IDE / non-classpath scenarios)
     * 3. Project root fallback
     */
    private Path resolveYamlPath() {
        String fileName = properties.getFile();

        // 1. Try classpath (this picks up src/test/resources/ during test phase)
        URL classpathUrl = getClass().getClassLoader().getResource(fileName);
        if (classpathUrl != null) {
            try {
                return Paths.get(classpathUrl.toURI());
            } catch (Exception e) {
                log.debug("ScanRest: Could not convert classpath URL to path: {}", e.getMessage());
            }
        }

        // 2. Try src/test/resources/ directly (for IDE / non-classpath scenarios)
        Path testResources = Path.of("src/test/resources", fileName);
        if (Files.exists(testResources)) {
            return testResources;
        }

        // 3. Fallback to project root
        Path rootPath = Path.of(fileName);
        if (Files.exists(rootPath)) {
            return rootPath;
        }

        return null;
    }

    /**
     * Create the appropriate test executor based on the configured mode.
     */
    private TestExecutor createExecutor(Path yamlPath) {
        Path basePath = yamlPath != null && yamlPath.getParent() != null ? yamlPath.getParent() : Path.of(".");

        if (properties.isMockMvcMode()) {
            return createMockMvcExecutor(basePath);
        }

        return new LiveHttpExecutor(basePath);
    }

    /**
     * Create a MockMvcExecutor using the existing Spring context.
     * Priority:
     * 1. Use injected MockMvc bean (from @WebMvcTest or manual configuration)
     * 2. Build MockMvc from the WebApplicationContext
     */
    private TestExecutor createMockMvcExecutor(Path basePath) {
        // 1. Use injected MockMvc if available (e.g., from @WebMvcTest)
        if (injectedMockMvc != null) {
            log.info("ScanRest: Using injected MockMvc bean (from @WebMvcTest or @AutoConfigureMockMvc)");
            return new MockMvcExecutor(injectedMockMvc, basePath);
        }

        // 2. Build MockMvc from the WebApplicationContext
        if (applicationContext instanceof WebApplicationContext webContext) {
            log.info("ScanRest: Building MockMvc from WebApplicationContext");
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();
            return new MockMvcExecutor(mockMvc, basePath);
        }

        // 3. Fallback: context is not a WebApplicationContext
        log.warn("ScanRest: MOCKMVC mode requires a WebApplicationContext. " +
                 "Use @SpringBootTest or @WebMvcTest. Falling back to LIVE mode.");
        return new LiveHttpExecutor(basePath);
    }

    private void autoGenerateYaml(Path yamlPath) {
        try {
            Path classesDir = Path.of("target/classes");
            if (!Files.exists(classesDir)) {
                classesDir = Path.of("build/classes/java/main"); // Gradle
            }
            if (!Files.exists(classesDir)) {
                log.warn("ScanRest: Cannot find compiled classes for auto-generation");
                return;
            }

            Files.createDirectories(yamlPath.getParent());
            List<ScannedEndpoint> endpoints = endpointScanner.scan(classesDir.toString());
            if (!endpoints.isEmpty()) {
                yamlGenerator.generate(endpoints, yamlPath,
                        "Auto-generated Tests", "http://localhost:8080");
                log.info("ScanRest: Generated {} with {} endpoints", yamlPath, endpoints.size());
            }
        } catch (Exception e) {
            log.warn("ScanRest: Failed to auto-generate YAML: {}", e.getMessage());
        }
    }

    public ScanRestProperties getProperties() {
        return properties;
    }
}
