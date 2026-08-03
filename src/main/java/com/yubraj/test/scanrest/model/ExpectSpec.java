package com.yubraj.test.scanrest.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expected response specification.
 * Supports both simple (just status code) and detailed (body assertions, headers, schema, save).
 *
 * <p><b>Simple:</b></p>
 * <pre>
 * expect: 200
 * </pre>
 *
 * <p><b>Detailed:</b></p>
 * <pre>
 * expect:
 *   status: 201
 *   body:
 *     $.id: NOT_NULL
 *     $.name: "John"
 *   headers:
 *     Location: CONTAINS("/api/users/")
 *   schema: "schemas/user.json"
 *   save:
 *     createdId: "$.id"
 * </pre>
 */
public class ExpectSpec {

    private int status;
    private Map<String, Object> body = new LinkedHashMap<>();  // JSONPath -> expected value or matcher
    private Map<String, Object> headers = new LinkedHashMap<>(); // header name -> value or matcher
    private String schema; // Path to JSON Schema file
    private Map<String, String> save = new LinkedHashMap<>(); // variable name -> JSONPath expression

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public Map<String, Object> getBody() { return body; }
    public void setBody(Map<String, Object> body) { this.body = body; }

    public Map<String, Object> getHeaders() { return headers; }
    public void setHeaders(Map<String, Object> headers) { this.headers = headers; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public Map<String, String> getSave() { return save; }
    public void setSave(Map<String, String> save) { this.save = save; }

    public boolean hasSave() { return save != null && !save.isEmpty(); }
}
