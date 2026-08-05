package io.scanrest.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.scanrest.engine.AssertionEngine;
import io.scanrest.engine.VariableResolver;
import io.scanrest.model.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Executes API tests against a live running server using RestAssured.
 * Supports variable interpolation, test chaining, parameterized tests, and auth overrides.
 */
public class LiveHttpExecutor implements TestExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveHttpExecutor.class);
    private final AssertionEngine assertionEngine = new AssertionEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path basePath;
    private String baseUrl; // set externally for executeSingle()

    public LiveHttpExecutor() {
        this.basePath = Path.of(".");
    }

    public LiveHttpExecutor(Path basePath) {
        this.basePath = basePath;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public List<TestResult> execute(TestSuiteSpec suite, VariableResolver resolver) {
        List<TestResult> results = new ArrayList<>();
        ScanRestConfig config = suite.getConfig();

        String baseUrl = config.resolveBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must be set in config for live HTTP mode");
        }

        // Apply global headers resolver
        Map<String, String> globalHeaders = resolver.resolveMap(config.getGlobalHeaders());

        log.info("Executing tests against: {}", baseUrl);

        // Track completed tests for dependsOn
        Map<String, TestResult> completedTests = new LinkedHashMap<>();

        for (TestSpec test : suite.getTests()) {
            if (test.isParameterized()) {
                // Expand parameterized tests
                for (int i = 0; i < test.getParameterized().size(); i++) {
                    Map<String, Object> params = test.getParameterized().get(i);
                    VariableResolver paramResolver = resolver.copy();
                    // Override expect if present in params
                    ExpectSpec paramExpect = null;
                    for (var entry : params.entrySet()) {
                        if ("expect".equals(entry.getKey())) {
                            paramExpect = buildExpectFromParam(entry.getValue());
                        } else {
                            paramResolver.set(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                    }

                    String name = "%s [%d] %s".formatted(test.displayName(), i + 1, params);
                    TestResult result = executeTest(
                            baseUrl, globalHeaders, test, paramResolver,
                            paramExpect != null ? paramExpect : test.getExpect(), name);
                    results.add(result);
                    completedTests.put(name, result);
                }
            } else {
                // Check dependsOn
                if (test.getDependsOn() != null) {
                    TestResult dep = completedTests.get(test.getDependsOn());
                    if (dep == null || !dep.isPassed()) {
                        TestResult skipped = new TestResult();
                        skipped.setTestCaseName(test.displayName());
                        skipped.setEndpointPath(test.getPath());
                        skipped.setHttpMethod(test.getMethod());
                        skipped.setStatus(TestResult.Status.ERROR);
                        skipped.setErrorMessage("Skipped: dependency '%s' not met".formatted(test.getDependsOn()));
                        skipped.setDuration(Duration.ZERO);
                        results.add(skipped);
                        completedTests.put(test.displayName(), skipped);
                        continue;
                    }
                }

                TestResult result = executeTest(
                        baseUrl, globalHeaders, test, resolver, test.getExpect(), test.displayName());
                results.add(result);
                completedTests.put(test.displayName(), result);
            }
        }

        return results;
    }

    @Override
    public TestResult executeSingle(TestSpec test, Map<String, String> globalHeaders,
                                    VariableResolver resolver, ExpectSpec expect, String displayName) {
        if (this.baseUrl == null) {
            throw new IllegalStateException("baseUrl must be set before calling executeSingle()");
        }
        return executeTest(this.baseUrl, globalHeaders, test, resolver, expect, displayName);
    }

    private TestResult executeTest(String baseUrl, Map<String, String> globalHeaders,
                                    TestSpec test, VariableResolver resolver,
                                    ExpectSpec expect, String displayName) {
        TestResult result = new TestResult();
        result.setEndpointPath(test.getPath());
        result.setHttpMethod(test.getMethod());
        result.setTestCaseName(displayName);

        Instant start = Instant.now();

        try {
            // Resolve path variables
            String resolvedPath = resolver.resolve(test.getPath());

            RequestSpecification spec = RestAssured.given()
                    .baseUri(baseUrl);

            // Apply auth override
            if (!"NONE".equalsIgnoreCase(test.getAuth())) {
                // Apply global headers (which may include Authorization)
                if (globalHeaders != null) {
                    spec.headers(globalHeaders);
                }
            }
            // If auth=NONE, skip global headers with Authorization

            // Apply test-specific headers
            if (test.getRequest() != null && test.getRequest().getHeaders() != null) {
                Map<String, String> resolvedHeaders = resolver.resolveMap(test.getRequest().getHeaders());
                spec.headers(resolvedHeaders);
            }

            // Apply query params
            if (test.getRequest() != null && test.getRequest().getQueryParams() != null) {
                Map<String, String> resolvedQp = resolver.resolveMap(test.getRequest().getQueryParams());
                spec.queryParams(resolvedQp);
            }

            // Apply request body
            if (test.getRequest() != null && test.getRequest().getBody() != null) {
                Object resolvedBody = resolver.resolveDeep(test.getRequest().getBody());
                String bodyStr;
                if (resolvedBody instanceof String s) {
                    bodyStr = s.trim();
                } else {
                    bodyStr = objectMapper.writeValueAsString(resolvedBody);
                }
                spec.body(bodyStr);
                spec.contentType("application/json");
            }

            // Execute request
            Response response = switch (test.getMethod()) {
                case GET -> spec.get(resolvedPath);
                case POST -> spec.post(resolvedPath);
                case PUT -> spec.put(resolvedPath);
                case DELETE -> spec.delete(resolvedPath);
                case PATCH -> spec.patch(resolvedPath);
                case HEAD -> spec.head(resolvedPath);
                case OPTIONS -> spec.options(resolvedPath);
            };

            result.setActualStatusCode(response.statusCode());
            result.setActualBody(response.body().asString());
            result.setDuration(Duration.between(start, Instant.now()));

            // Validate response
            if (expect != null) {
                Map<String, String> responseHeaders = new LinkedHashMap<>();
                response.headers().asList().forEach(h -> responseHeaders.put(h.getName(), h.getValue()));

                List<String> failures = assertionEngine.validate(
                        expect, response.statusCode(), response.body().asString(),
                        responseHeaders, resolver, basePath);

                result.setFailures(failures);
                result.setStatus(failures.isEmpty() ? TestResult.Status.PASSED : TestResult.Status.FAILED);
            } else {
                result.setStatus(TestResult.Status.PASSED);
            }

            log.info("[{}] {} {} - {} ({}ms)",
                    result.getStatus(), test.getMethod(), test.getPath(),
                    displayName, result.getDuration().toMillis());

        } catch (Exception e) {
            result.setDuration(Duration.between(start, Instant.now()));
            result.setStatus(TestResult.Status.ERROR);
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isBlank()) {
                errorMsg = e.getClass().getSimpleName();
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    errorMsg += ": " + e.getCause().getMessage();
                }
            }
            result.setErrorMessage(errorMsg);
            log.error("[ERROR] {} {} - {}: {}", test.getMethod(), test.getPath(), displayName, errorMsg, e);
        }

        return result;
    }

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
}
