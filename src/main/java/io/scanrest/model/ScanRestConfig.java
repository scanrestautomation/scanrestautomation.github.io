package io.scanrest.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level config block from the YAML file.
 *
 * <pre>
 * config:
 *   name: "User Service API Tests"
 *   baseUrl: "http://localhost:8080"
 *   autoScan: true
 *   activeProfile: dev
 *   profiles:
 *     dev:
 *       baseUrl: "http://localhost:8080"
 *       token: "dev-token"
 *   globalHeaders:
 *     Content-Type: "application/json"
 *   variables:
 *     userId: "101"
 * </pre>
 */
public class ScanRestConfig {

    private String name;
    private String baseUrl;
    private boolean autoScan;
    private String activeProfile;
    private Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
    private Map<String, String> globalHeaders = new LinkedHashMap<>();
    private Map<String, String> variables = new LinkedHashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isAutoScan() { return autoScan; }
    public void setAutoScan(boolean autoScan) { this.autoScan = autoScan; }

    public String getActiveProfile() { return activeProfile; }
    public void setActiveProfile(String activeProfile) { this.activeProfile = activeProfile; }

    public Map<String, Map<String, String>> getProfiles() { return profiles; }
    public void setProfiles(Map<String, Map<String, String>> profiles) { this.profiles = profiles; }

    public Map<String, String> getGlobalHeaders() { return globalHeaders; }
    public void setGlobalHeaders(Map<String, String> globalHeaders) { this.globalHeaders = globalHeaders; }

    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }

    /**
     * Resolve the effective baseUrl: profile overrides top-level.
     */
    public String resolveBaseUrl() {
        if (activeProfile != null && profiles.containsKey(activeProfile)) {
            String profileUrl = profiles.get(activeProfile).get("baseUrl");
            if (profileUrl != null) return profileUrl;
        }
        return baseUrl;
    }

    /**
     * Merge profile variables into the global variables map.
     */
    public Map<String, String> resolveVariables() {
        Map<String, String> resolved = new LinkedHashMap<>(variables);
        if (activeProfile != null && profiles.containsKey(activeProfile)) {
            resolved.putAll(profiles.get(activeProfile));
        }
        return resolved;
    }
}
