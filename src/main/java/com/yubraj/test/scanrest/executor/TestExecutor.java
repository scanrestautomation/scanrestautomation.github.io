package com.yubraj.test.scanrest.executor;

import com.yubraj.test.scanrest.engine.VariableResolver;
import com.yubraj.test.scanrest.model.TestResult;
import com.yubraj.test.scanrest.model.TestSuiteSpec;

import java.util.List;

/**
 * Common interface for test executors (live HTTP and embedded Spring).
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
}
