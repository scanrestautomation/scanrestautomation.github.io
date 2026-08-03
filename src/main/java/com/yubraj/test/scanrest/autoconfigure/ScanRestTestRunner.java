package com.yubraj.test.scanrest.autoconfigure;

import com.yubraj.test.scanrest.config.YamlParser;
import com.yubraj.test.scanrest.engine.VariableResolver;
import com.yubraj.test.scanrest.executor.EmbeddedSpringExecutor;
import com.yubraj.test.scanrest.executor.LiveHttpExecutor;
import com.yubraj.test.scanrest.executor.TestExecutor;
import com.yubraj.test.scanrest.generator.YamlGenerator;
import com.yubraj.test.scanrest.model.ScannedEndpoint;
import com.yubraj.test.scanrest.model.TestResult;
import com.yubraj.test.scanrest.model.TestSuiteSpec;
import com.yubraj.test.scanrest.report.TestReporter;
import com.yubraj.test.scanrest.scanner.EndpointScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Executes ScanRest tests within the Spring Boot lifecycle.
 *
 * <p>The YAML test file is resolved from the classpath first
 * (i.e. {@code src/test/resources/scanrest-tests.yml}), then falls back
 * to the project root.</p>
 *
 * <p>Automatically detects the running server port from the Spring
 * {@link Environment} (supports {@code local.server.port} for
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}).</p>
 *
 * <p>Can run tests:</p>
 * <ul>
 *   <li>Automatically on startup ({@code scanrest.run-on-startup=true})</li>
 *   <li>Programmatically by injecting this bean and calling {@link #runTests()}</li>
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

    public ScanRestTestRunner(ScanRestProperties properties,
                               YamlParser yamlParser,
                               EndpointScanner endpointScanner,
                               YamlGenerator yamlGenerator,
                               TestReporter testReporter,
                               Environment environment) {
        this.properties = properties;
        this.yamlParser = yamlParser;
        this.endpointScanner = endpointScanner;
        this.yamlGenerator = yamlGenerator;
        this.testReporter = testReporter;
        this.environment = environment;
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
        String resolvedBaseUrl = resolveBaseUrl();

        log.info("==================================================");
        log.info("  ScanRest is ACTIVE");
        log.info("==================================================");
        log.info("  Test file     : {}", properties.getFile());
        log.info("  Mode          : {}", properties.getMode());
        log.info("  Base URL      : {}", resolvedBaseUrl);
        log.info("  Profile       : {}", properties.getProfile() != null ? properties.getProfile() : "(default)");
        log.info("  Run on startup: {}", properties.isRunOnStartup());
        log.info("  Fail on error : {}", properties.isFailOnError());

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
     * Run all tests defined in the YAML file.
     *
     * @return list of test results
     */
    public List<TestResult> runTests() {
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
                return List.of();
            }

            // Parse YAML
            TestSuiteSpec suite = yamlParser.parse(yamlPath);

            // Override profile from properties
            if (properties.getProfile() != null) {
                suite.getConfig().setActiveProfile(properties.getProfile());
            }

            // Override baseUrl: properties > auto-detected port > YAML config
            String effectiveBaseUrl = resolveBaseUrl();
            suite.getConfig().setBaseUrl(effectiveBaseUrl);

            // Build resolver
            VariableResolver resolver = new VariableResolver(suite.getConfig().resolveVariables());

            // Create executor
            TestExecutor executor = createExecutor(yamlPath);

            // Execute
            List<TestResult> results = executor.execute(suite, resolver);

            // Report
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
            errorResult.setDuration(java.time.Duration.ZERO);
            return List.of(errorResult);
        }
    }

    /**
     * Resolve the effective base URL.
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

    private TestExecutor createExecutor(Path yamlPath) {
        return switch (properties.getMode()) {
            case LIVE -> new LiveHttpExecutor(
                    yamlPath.getParent() != null ? yamlPath.getParent() : Path.of("."));
            case EMBEDDED -> {
                log.warn("ScanRest: Embedded mode requires --app-class in CLI. " +
                         "In Spring Boot context, use live mode or inject MockMvc directly.");
                yield new LiveHttpExecutor(
                        yamlPath.getParent() != null ? yamlPath.getParent() : Path.of("."));
            }
        };
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
