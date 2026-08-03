package com.yubraj.test.scanrest.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yubraj.test.scanrest.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the new ScanRest YAML format supporting both simple and detailed test definitions.
 *
 * <p><b>Simple format:</b></p>
 * <pre>
 * tests:
 *   - GET /api/users:
 *       expect: 200
 * </pre>
 *
 * <p><b>Detailed format:</b></p>
 * <pre>
 * tests:
 *   - name: "Create user"
 *     path: /api/users
 *     method: POST
 *     ...
 * </pre>
 */
public class YamlParser {

    private static final Logger log = LoggerFactory.getLogger(YamlParser.class);
    private static final Pattern SIMPLE_KEY_PATTERN = Pattern.compile("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$");

    private final ObjectMapper mapper;

    public YamlParser() {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.findAndRegisterModules();
    }

    /**
     * Parse a YAML file into a TestSuiteSpec.
     */
    public TestSuiteSpec parse(Path yamlPath) throws IOException {
        log.info("Parsing YAML test file: {}", yamlPath);

        if (!Files.exists(yamlPath)) {
            throw new IOException("YAML file not found: " + yamlPath);
        }

        String content = Files.readString(yamlPath);
        return parseString(content);
    }

    /**
     * Parse YAML content string into a TestSuiteSpec.
     */
    @SuppressWarnings("unchecked")
    public TestSuiteSpec parseString(String yamlContent) throws IOException {
        Map<String, Object> raw = mapper.readValue(yamlContent, Map.class);

        TestSuiteSpec suite = new TestSuiteSpec();

        // Parse config block
        if (raw.containsKey("config")) {
            ScanRestConfig config = mapper.convertValue(raw.get("config"), ScanRestConfig.class);
            suite.setConfig(config);
        } else {
            suite.setConfig(new ScanRestConfig());
        }

        // Parse tests
        if (raw.containsKey("tests")) {
            List<Object> testsList = (List<Object>) raw.get("tests");
            List<TestSpec> tests = new ArrayList<>();
            for (Object entry : testsList) {
                if (entry instanceof Map<?, ?> map) {
                    tests.add(parseTestEntry((Map<String, Object>) map));
                }
            }
            suite.setTests(tests);
        }

        log.info("Loaded test suite '{}' with {} tests",
                suite.getConfig().getName(), suite.getTests().size());

        validate(suite);
        return suite;
    }

    /**
     * Parse a single test entry. Detects simple vs detailed format.
     */
    @SuppressWarnings("unchecked")
    private TestSpec parseTestEntry(Map<String, Object> map) {
        // Check for simple format: key is "GET /api/users" etc.
        for (var entry : map.entrySet()) {
            Matcher m = SIMPLE_KEY_PATTERN.matcher(entry.getKey());
            if (m.matches()) {
                return parseSimpleTest(m.group(1), m.group(2), entry.getValue());
            }
        }

        // Detailed format
        return parseDetailedTest(map);
    }

    /**
     * Parse simple format: "GET /api/users": { expect: 200 } or { expect: { status: 200, body: {...} } }
     */
    @SuppressWarnings("unchecked")
    private TestSpec parseSimpleTest(String method, String path, Object value) {
        TestSpec spec = new TestSpec();
        spec.setMethod(HttpMethod.valueOf(method));
        spec.setPath(path);
        spec.setSimpleFormat(true);

        if (value instanceof Map<?, ?> details) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            spec.setExpect(parseExpect(detailsMap.get("expect")));

            // Simple format can also have body assertions directly
            if (detailsMap.containsKey("body") && spec.getExpect() != null) {
                Object bodyVal = detailsMap.get("body");
                if (bodyVal instanceof Map<?, ?> bodyMap) {
                    spec.getExpect().setBody((Map<String, Object>) bodyMap);
                }
            }
        } else if (value instanceof Integer statusCode) {
            // Ultra-simple: - GET /api/users: 200
            ExpectSpec expect = new ExpectSpec();
            expect.setStatus(statusCode);
            spec.setExpect(expect);
        }

        return spec;
    }

    /**
     * Parse detailed format with all fields.
     */
    @SuppressWarnings("unchecked")
    private TestSpec parseDetailedTest(Map<String, Object> map) {
        TestSpec spec = new TestSpec();
        spec.setSimpleFormat(false);

        spec.setName((String) map.get("name"));
        spec.setPath((String) map.get("path"));

        if (map.get("method") != null) {
            spec.setMethod(HttpMethod.valueOf(String.valueOf(map.get("method")).toUpperCase()));
        }

        // Parse request
        if (map.containsKey("request")) {
            spec.setRequest(parseRequest((Map<String, Object>) map.get("request")));
        }

        // Parse expect
        if (map.containsKey("expect")) {
            spec.setExpect(parseExpect(map.get("expect")));
        }

        spec.setDependsOn((String) map.get("dependsOn"));
        spec.setAuth((String) map.get("auth"));

        // Parse parameterized
        if (map.containsKey("parameterized")) {
            spec.setParameterized((List<Map<String, Object>>) map.get("parameterized"));
        }

        return spec;
    }

    /**
     * Parse expect block. Can be:
     * - Integer: just a status code
     * - Map: { status: 200, body: {...}, headers: {...}, schema: "...", save: {...} }
     */
    @SuppressWarnings("unchecked")
    private ExpectSpec parseExpect(Object value) {
        if (value == null) return null;

        ExpectSpec expect = new ExpectSpec();

        if (value instanceof Integer statusCode) {
            expect.setStatus(statusCode);
            return expect;
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> expectMap = (Map<String, Object>) map;

            if (expectMap.containsKey("status")) {
                expect.setStatus(((Number) expectMap.get("status")).intValue());
            }

            if (expectMap.containsKey("body")) {
                Object body = expectMap.get("body");
                if (body instanceof Map<?, ?> bodyMap) {
                    expect.setBody((Map<String, Object>) bodyMap);
                }
            }

            if (expectMap.containsKey("headers")) {
                Object headers = expectMap.get("headers");
                if (headers instanceof Map<?, ?> headersMap) {
                    expect.setHeaders((Map<String, Object>) headersMap);
                }
            }

            if (expectMap.containsKey("schema")) {
                expect.setSchema(String.valueOf(expectMap.get("schema")));
            }

            if (expectMap.containsKey("save")) {
                Object save = expectMap.get("save");
                if (save instanceof Map<?, ?> saveMap) {
                    Map<String, String> saveSpec = new LinkedHashMap<>();
                    for (var entry : saveMap.entrySet()) {
                        saveSpec.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                    expect.setSave(saveSpec);
                }
            }
        }

        return expect;
    }

    /**
     * Parse request block.
     */
    @SuppressWarnings("unchecked")
    private RequestSpec parseRequest(Map<String, Object> map) {
        RequestSpec request = new RequestSpec();

        if (map.containsKey("headers")) {
            Object headers = map.get("headers");
            if (headers instanceof Map<?, ?> headersMap) {
                Map<String, String> headerStrMap = new LinkedHashMap<>();
                for (var entry : headersMap.entrySet()) {
                    headerStrMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
                request.setHeaders(headerStrMap);
            }
        }

        if (map.containsKey("queryParams")) {
            Object qp = map.get("queryParams");
            if (qp instanceof Map<?, ?> qpMap) {
                Map<String, String> qpStrMap = new LinkedHashMap<>();
                for (var entry : qpMap.entrySet()) {
                    qpStrMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
                request.setQueryParams(qpStrMap);
            }
        }

        if (map.containsKey("body")) {
            request.setBody(map.get("body"));
        }

        return request;
    }

    private void validate(TestSuiteSpec suite) {
        for (TestSpec test : suite.getTests()) {
            if (test.getPath() == null || test.getPath().isBlank()) {
                throw new IllegalArgumentException("Test path must not be blank: " + test);
            }
            if (test.getMethod() == null) {
                throw new IllegalArgumentException("Test method must be specified for: " + test.getPath());
            }
        }
    }
}
