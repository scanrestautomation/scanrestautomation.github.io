package io.scanrest.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single test definition from the YAML.
 * Supports both simple and detailed formats.
 *
 * <p><b>Simple format:</b></p>
 * <pre>
 * - GET /api/users:
 *     expect: 200
 * </pre>
 *
 * <p><b>Detailed format:</b></p>
 * <pre>
 * - name: "Create user"
 *   path: /api/users
 *   method: POST
 *   request:
 *     body:
 *       name: "John"
 *   expect:
 *     status: 201
 *     body:
 *       $.id: NOT_NULL
 *     save:
 *       createdId: "$.id"
 *   dependsOn: "Other test"
 *   auth: NONE
 *   parameterized:
 *     - { id: "1", expect: 200 }
 * </pre>
 */
public class TestSpec {

    private String name;
    private String path;
    private HttpMethod method;
    private RequestSpec request;
    private ExpectSpec expect;
    private String dependsOn;
    private String auth;
    private List<Map<String, Object>> parameterized;

    // For simple format parsing
    private boolean simpleFormat;

    public TestSpec() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public HttpMethod getMethod() { return method; }
    public void setMethod(HttpMethod method) { this.method = method; }

    public RequestSpec getRequest() { return request; }
    public void setRequest(RequestSpec request) { this.request = request; }

    public ExpectSpec getExpect() { return expect; }
    public void setExpect(ExpectSpec expect) { this.expect = expect; }

    public String getDependsOn() { return dependsOn; }
    public void setDependsOn(String dependsOn) { this.dependsOn = dependsOn; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    public List<Map<String, Object>> getParameterized() { return parameterized; }
    public void setParameterized(List<Map<String, Object>> parameterized) { this.parameterized = parameterized; }

    public boolean isSimpleFormat() { return simpleFormat; }
    public void setSimpleFormat(boolean simpleFormat) { this.simpleFormat = simpleFormat; }

    public boolean isParameterized() {
        return parameterized != null && !parameterized.isEmpty();
    }

    /**
     * Generate a display name for this test.
     */
    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        return "%s %s".formatted(method, path);
    }

    @Override
    public String toString() {
        return "TestSpec{%s %s, name='%s'}".formatted(method, path, name);
    }
}
