package com.yubraj.test.scanrest.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubraj.test.scanrest.engine.AssertionEngine;
import com.yubraj.test.scanrest.engine.VariableResolver;
import com.yubraj.test.scanrest.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Executes API tests using an existing Spring MockMvc instance.
 *
 * <p>Unlike {@link EmbeddedSpringExecutor} which boots its own context,
 * this executor uses the MockMvc from the user's existing Spring test context.
 * This means it automatically respects:</p>
 * <ul>
 *   <li>{@code @MockBean} replacements</li>
 *   <li>{@code @TestConfiguration} overrides</li>
 *   <li>{@code @ActiveProfiles} test profiles</li>
 *   <li>{@code @WebMvcTest} controller slices</li>
 * </ul>
 */
public class MockMvcExecutor implements TestExecutor {

    private static final Logger log = LoggerFactory.getLogger(MockMvcExecutor.class);
    private final AssertionEngine assertionEngine = new AssertionEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockMvc mockMvc;
    private final Path basePath;

    public MockMvcExecutor(MockMvc mockMvc) {
        this(mockMvc, Path.of("."));
    }

    public MockMvcExecutor(MockMvc mockMvc, Path basePath) {
        this.mockMvc = mockMvc;
        this.basePath = basePath;
    }

    @Override
    public List<TestResult> execute(TestSuiteSpec suite, VariableResolver resolver) {
        List<TestResult> results = new ArrayList<>();
        ScanRestConfig config = suite.getConfig();
        Map<String, String> globalHeaders = resolver.resolveMap(config.getGlobalHeaders());

        log.info("Executing tests via MockMvc (using existing Spring context)");

        Map<String, TestResult> completedTests = new LinkedHashMap<>();

        for (TestSpec test : suite.getTests()) {
            if (test.isParameterized()) {
                for (int i = 0; i < test.getParameterized().size(); i++) {
                    Map<String, Object> params = test.getParameterized().get(i);
                    VariableResolver paramResolver = resolver.copy();
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
                            globalHeaders, test, paramResolver,
                            paramExpect != null ? paramExpect : test.getExpect(), name);
                    results.add(result);
                    completedTests.put(name, result);
                }
            } else {
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
                        globalHeaders, test, resolver, test.getExpect(), test.displayName());
                results.add(result);
                completedTests.put(test.displayName(), result);
            }
        }

        return results;
    }

    private TestResult executeTest(Map<String, String> globalHeaders,
                                    TestSpec test, VariableResolver resolver,
                                    ExpectSpec expect, String displayName) {
        TestResult result = new TestResult();
        result.setEndpointPath(test.getPath());
        result.setHttpMethod(test.getMethod());
        result.setTestCaseName(displayName);

        Instant start = Instant.now();

        try {
            String resolvedPath = resolver.resolve(test.getPath());

            MockHttpServletRequestBuilder requestBuilder = switch (test.getMethod()) {
                case GET -> MockMvcRequestBuilders.get(resolvedPath);
                case POST -> MockMvcRequestBuilders.post(resolvedPath);
                case PUT -> MockMvcRequestBuilders.put(resolvedPath);
                case DELETE -> MockMvcRequestBuilders.delete(resolvedPath);
                case PATCH -> MockMvcRequestBuilders.patch(resolvedPath);
                case HEAD -> MockMvcRequestBuilders.head(resolvedPath);
                case OPTIONS -> MockMvcRequestBuilders.options(resolvedPath);
            };

            // Apply headers
            if (!"NONE".equalsIgnoreCase(test.getAuth()) && globalHeaders != null) {
                globalHeaders.forEach(requestBuilder::header);
            }
            if (test.getRequest() != null && test.getRequest().getHeaders() != null) {
                Map<String, String> resolvedHeaders = resolver.resolveMap(test.getRequest().getHeaders());
                resolvedHeaders.forEach(requestBuilder::header);
            }

            // Apply query params
            if (test.getRequest() != null && test.getRequest().getQueryParams() != null) {
                Map<String, String> resolvedQp = resolver.resolveMap(test.getRequest().getQueryParams());
                resolvedQp.forEach(requestBuilder::queryParam);
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
                requestBuilder.content(bodyStr);
                requestBuilder.contentType(MediaType.APPLICATION_JSON);
            }

            MvcResult mvcResult = mockMvc.perform(requestBuilder).andReturn();

            int statusCode = mvcResult.getResponse().getStatus();
            String responseBody = mvcResult.getResponse().getContentAsString();

            result.setActualStatusCode(statusCode);
            result.setActualBody(responseBody);
            result.setDuration(Duration.between(start, Instant.now()));

            if (expect != null) {
                Map<String, String> responseHeaders = new LinkedHashMap<>();
                for (String headerName : mvcResult.getResponse().getHeaderNames()) {
                    responseHeaders.put(headerName, mvcResult.getResponse().getHeader(headerName));
                }

                List<String> failures = assertionEngine.validate(
                        expect, statusCode, responseBody, responseHeaders, resolver, basePath);

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
