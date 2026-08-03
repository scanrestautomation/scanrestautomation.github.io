package com.yubraj.test.scanrest.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.yubraj.test.scanrest.model.ExpectSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Validates actual HTTP responses against {@link ExpectSpec} using JSONPath and matchers.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Status code comparison</li>
 *   <li>JSONPath body assertions with matcher expressions</li>
 *   <li>Header assertions with matchers</li>
 *   <li>JSON Schema validation</li>
 *   <li>Saving response values for test chaining</li>
 * </ul>
 */
public class AssertionEngine {

    private static final Logger log = LoggerFactory.getLogger(AssertionEngine.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
            .build();

    /**
     * Validate the actual response against the expected spec.
     *
     * @param expect         expected spec
     * @param actualStatus   actual HTTP status code
     * @param actualBody     actual response body string
     * @param actualHeaders  actual response headers
     * @param resolver       variable resolver (to save extracted values into)
     * @param basePath       base path for resolving schema files
     * @return list of failure messages (empty = all passed)
     */
    public List<String> validate(ExpectSpec expect, int actualStatus, String actualBody,
                                  Map<String, String> actualHeaders, VariableResolver resolver,
                                  Path basePath) {
        List<String> failures = new ArrayList<>();

        if (expect == null) return failures;

        // 1. Status code
        if (expect.getStatus() != 0 && expect.getStatus() != actualStatus) {
            failures.add("Status: expected %d but got %d".formatted(expect.getStatus(), actualStatus));
        }

        // 2. Body assertions (JSONPath)
        if (expect.getBody() != null && !expect.getBody().isEmpty() && actualBody != null && !actualBody.isBlank()) {
            validateBody(expect.getBody(), actualBody, failures);
        }

        // 3. Header assertions
        if (expect.getHeaders() != null && !expect.getHeaders().isEmpty()) {
            validateHeaders(expect.getHeaders(), actualHeaders, failures);
        }

        // 4. JSON Schema validation
        if (expect.getSchema() != null && !expect.getSchema().isBlank()) {
            validateSchema(expect.getSchema(), actualBody, failures, basePath);
        }

        // 5. Save values (even if there are failures - save what we can)
        if (expect.hasSave() && actualBody != null && resolver != null) {
            saveValues(expect.getSave(), actualBody, resolver);
        }

        return failures;
    }

    private void validateBody(Map<String, Object> bodyAssertions, String actualBody, List<String> failures) {
        Object document;
        try {
            document = Configuration.defaultConfiguration().jsonProvider().parse(actualBody);
        } catch (Exception e) {
            failures.add("Cannot parse response body as JSON: " + e.getMessage());
            return;
        }

        for (var entry : bodyAssertions.entrySet()) {
            String jsonPathExpr = entry.getKey();
            Object expectedValue = entry.getValue();

            try {
                Object actualValue = JsonPath.using(JSON_PATH_CONFIG).parse(document).read(jsonPathExpr);
                String error = MatcherEngine.evaluate(expectedValue, actualValue);
                if (error != null) {
                    failures.add("%s: %s".formatted(jsonPathExpr, error));
                }
            } catch (PathNotFoundException e) {
                failures.add("%s: path not found in response".formatted(jsonPathExpr));
            } catch (Exception e) {
                failures.add("%s: error evaluating - %s".formatted(jsonPathExpr, e.getMessage()));
            }
        }
    }

    private void validateHeaders(Map<String, Object> expectedHeaders, Map<String, String> actualHeaders,
                                  List<String> failures) {
        for (var entry : expectedHeaders.entrySet()) {
            String headerName = entry.getKey();
            Object expectedValue = entry.getValue();

            String actualValue = findHeaderIgnoreCase(actualHeaders, headerName);
            String error = MatcherEngine.evaluate(expectedValue, actualValue);
            if (error != null) {
                failures.add("Header '%s': %s".formatted(headerName, error));
            }
        }
    }

    private void validateSchema(String schemaPath, String actualBody, List<String> failures, Path basePath) {
        try {
            Path schemaFile = basePath != null ? basePath.resolve(schemaPath) : Path.of(schemaPath);
            if (!Files.exists(schemaFile)) {
                failures.add("Schema file not found: " + schemaFile);
                return;
            }

            String schemaContent = Files.readString(schemaFile);
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            JsonNode bodyNode = objectMapper.readTree(actualBody);

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(bodyNode);

            if (!errors.isEmpty()) {
                for (ValidationMessage msg : errors) {
                    failures.add("Schema: " + msg.getMessage());
                }
            }
        } catch (IOException e) {
            failures.add("Schema validation error: " + e.getMessage());
        }
    }

    private void saveValues(Map<String, String> saveSpec, String actualBody, VariableResolver resolver) {
        try {
            Object document = Configuration.defaultConfiguration().jsonProvider().parse(actualBody);
            for (var entry : saveSpec.entrySet()) {
                String varName = entry.getKey();
                String jsonPathExpr = entry.getValue();
                try {
                    Object value = JsonPath.using(JSON_PATH_CONFIG).parse(document).read(jsonPathExpr);
                    if (value != null) {
                        resolver.set(varName, String.valueOf(value));
                        log.debug("Saved variable: {} = {}", varName, value);
                    }
                } catch (Exception e) {
                    log.warn("Failed to save variable '{}' from path '{}': {}", varName, jsonPathExpr, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse body for saving variables: {}", e.getMessage());
        }
    }

    private String findHeaderIgnoreCase(Map<String, String> headers, String key) {
        if (headers == null) return null;
        for (var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
