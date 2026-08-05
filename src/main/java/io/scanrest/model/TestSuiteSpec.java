package io.scanrest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root model representing the entire YAML test specification.
 *
 * <pre>
 * config:
 *   name: "User Service API Tests"
 *   baseUrl: "http://localhost:8080"
 *   ...
 * tests:
 *   - GET /api/users:
 *       expect: 200
 *   - name: "Create user"
 *     ...
 * </pre>
 */
public class TestSuiteSpec {

    private ScanRestConfig config;
    private List<TestSpec> tests = new ArrayList<>();

    public ScanRestConfig getConfig() { return config; }
    public void setConfig(ScanRestConfig config) { this.config = config; }

    public List<TestSpec> getTests() { return tests; }
    public void setTests(List<TestSpec> tests) { this.tests = tests; }

    @Override
    public String toString() {
        return "TestSuiteSpec{name='%s', tests=%d}".formatted(
                config != null ? config.getName() : "unnamed", tests.size());
    }
}
