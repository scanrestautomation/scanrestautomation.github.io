package io.scanrest.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request specification for a test case.
 *
 * <pre>
 * request:
 *   headers:
 *     X-Custom: "value"
 *   queryParams:
 *     page: "0"
 *   body:
 *     name: "John"
 *     email: "john@example.com"
 * </pre>
 */
public class RequestSpec {

    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, String> queryParams = new LinkedHashMap<>();
    private Object body; // Can be Map (JSON object), String, or List

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public Map<String, String> getQueryParams() { return queryParams; }
    public void setQueryParams(Map<String, String> queryParams) { this.queryParams = queryParams; }

    public Object getBody() { return body; }
    public void setBody(Object body) { this.body = body; }
}
