package com.yubraj.test.scanrest.executor;

import com.yubraj.test.scanrest.engine.VariableResolver;
import com.yubraj.test.scanrest.model.ExpectSpec;
import com.yubraj.test.scanrest.model.TestResult;
import com.yubraj.test.scanrest.model.TestSpec;
import com.yubraj.test.scanrest.model.TestSuiteSpec;

import java.util.List;
import java.util.Map;

/**
 * Common interface for test executors (live HTTP, MockMvc, and embedded Spring).
 */
public interface TestExecutor {

    /**
     * Execute all test cases defined in the test suite.
     *
     * @param suite    the parsed test suite
     * @param resolver variable resolver with profile/config variables pre-loaded
     * @return list of test results
     */
    List<TestResult> execute(TestSuiteSpec suite, VariableResolver resolver);

    /**
     * Execute a single test case.
     * Used by {@code @TestFactory} / {@code DynamicTest} integration to run
     * each YAML test case as an individual JUnit test.
     *
     * @param test          the test specification
     * @param globalHeaders global headers to apply
     * @param resolver      variable resolver (shared across tests for chaining)
     * @param expect        expected response (may be overridden for parameterized tests)
     * @param displayName   display name for this test
     * @return the test result
     */
    TestResult executeSingle(TestSpec test, Map<String, String> globalHeaders,
                             VariableResolver resolver, ExpectSpec expect, String displayName);
}
