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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Executes ScanRest tests within the Spring Boot lifecycle.
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

    public ScanRestTestRunner(ScanRestProperties properties,
                               YamlParser yamlParser,
                               EndpointScanner endpointScanner,
                               YamlGenerator yamlGenerator,
                               TestReporter testReporter) {
        this.properties = properties;
        this.yamlParser = yamlParser;
        this.endpointScanner = endpointScanner;
        this.yamlGenerator = yamlGenerator;
        this.testReporter = testReporter;
    }

    /**
     * Auto-run tests on application startup if configured.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled() || !properties.isRunOnStartup()) {
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
            Path yamlPath = Path.of(properties.getFile());

            // Auto-generate YAML if enabled and file doesn't exist
            if (!Files.exists(yamlPath) && properties.isAutoGenerate()) {
                log.info("ScanRest: Test file not found. Auto-generating: {}", yamlPath);
                autoGenerateYaml(yamlPath);
            }

            if (!Files.exists(yamlPath)) {
                log.warn("ScanRest: Test file not found: {}. Skipping tests.", yamlPath);
                return List.of();
            }

            // Parse YAML
            TestSuiteSpec suite = yamlParser.parse(yamlPath);

            // Override profile from properties
            if (properties.getProfile() != null) {
                suite.getConfig().setActiveProfile(properties.getProfile());
            }

            // Override baseUrl from properties
            if (properties.getBaseUrl() != null) {
                suite.getConfig().setBaseUrl(properties.getBaseUrl());
            }

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
            // Try to find compiled classes
            Path classesDir = Path.of("target/classes");
            if (!Files.exists(classesDir)) {
                classesDir = Path.of("build/classes/java/main"); // Gradle
            }
            if (!Files.exists(classesDir)) {
                log.warn("ScanRest: Cannot find compiled classes for auto-generation");
                return;
            }

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
