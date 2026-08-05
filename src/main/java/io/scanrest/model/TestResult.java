package io.scanrest.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of executing a single test case.
 */
public class TestResult {

    public enum Status {
        PASSED, FAILED, ERROR
    }

    private String endpointPath;
    private HttpMethod httpMethod;
    private String testCaseName;
    private Status status;
    private int actualStatusCode;
    private String actualBody;
    private Duration duration;
    private List<String> failures = new ArrayList<>();
    private String errorMessage;

    public TestResult() {}

    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }

    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }

    public String getTestCaseName() { return testCaseName; }
    public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getActualStatusCode() { return actualStatusCode; }
    public void setActualStatusCode(int actualStatusCode) { this.actualStatusCode = actualStatusCode; }

    public String getActualBody() { return actualBody; }
    public void setActualBody(String actualBody) { this.actualBody = actualBody; }

    public Duration getDuration() { return duration; }
    public void setDuration(Duration duration) { this.duration = duration; }

    public List<String> getFailures() { return failures; }
    public void setFailures(List<String> failures) { this.failures = failures; }
    public void addFailure(String failure) { this.failures.add(failure); }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isPassed() { return status == Status.PASSED; }
}
