package com.yubraj.test.scanrest.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{variable}} placeholders in strings using a context map.
 * Variables can come from config, profiles, saved response values, or parameterized data.
 *
 * <pre>
 * variables: { userId: "101", token: "abc" }
 * input:     "/api/users/{{userId}}"
 * output:    "/api/users/101"
 * </pre>
 */
public class VariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> variables = new LinkedHashMap<>();

    public VariableResolver() {}

    public VariableResolver(Map<String, String> initialVars) {
        if (initialVars != null) {
            variables.putAll(initialVars);
        }
    }

    /**
     * Add or update a variable.
     */
    public void set(String key, String value) {
        variables.put(key, value);
    }

    /**
     * Add multiple variables at once.
     */
    public void putAll(Map<String, String> vars) {
        if (vars != null) variables.putAll(vars);
    }

    /**
     * Get current value of a variable.
     */
    public String get(String key) {
        return variables.get(key);
    }

    /**
     * Resolve all {{variable}} placeholders in the given string.
     * Unresolved variables are left as-is.
     */
    public String resolve(String input) {
        if (input == null) return null;
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve variables in all values of a map.
     */
    public Map<String, String> resolveMap(Map<String, String> map) {
        if (map == null) return null;
        Map<String, String> resolved = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue()));
        }
        return resolved;
    }

    /**
     * Resolve variables recursively in an object (String, Map, or other).
     * Used for request bodies which can be nested maps.
     */
    @SuppressWarnings("unchecked")
    public Object resolveDeep(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String s) {
            return resolve(s);
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()), resolveDeep(entry.getValue()));
            }
            return resolved;
        }
        if (obj instanceof java.util.List<?> list) {
            return list.stream().map(this::resolveDeep).toList();
        }
        return obj; // numbers, booleans, etc.
    }

    /**
     * Create a snapshot copy of this resolver (for parameterized tests).
     */
    public VariableResolver copy() {
        return new VariableResolver(new LinkedHashMap<>(variables));
    }

    public Map<String, String> getAll() {
        return new LinkedHashMap<>(variables);
    }
}
