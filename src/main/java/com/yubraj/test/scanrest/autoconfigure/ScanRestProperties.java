package com.yubraj.test.scanrest.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ScanRest.
 *
 * <p>Add these to your {@code application.properties} or {@code application.yml}:</p>
 *
 * <pre>
 * # Enable/disable ScanRest
 * scanrest.enabled=true
 *
 * # Path to the YAML test specification file
 * scanrest.file=scanrest-tests.yml
 *
 * # Execution mode: live (HTTP against running server) or embedded (MockMvc)
 * scanrest.mode=embedded
 *
 * # Active profile (overrides config.activeProfile in YAML)
 * scanrest.profile=dev
 *
 * # Base URL override (for live mode)
 * scanrest.base-url=http://localhost:8080
 *
 * # Whether to auto-scan controllers and generate YAML if file not found
 * scanrest.auto-generate=false
 *
 * # Whether to fail the build on test failure
 * scanrest.fail-on-error=true
 *
 * # Path to write the test report file
 * scanrest.report-path=target/scanrest-report.txt
 *
 * # Run tests on application startup (only in test phase)
 * scanrest.run-on-startup=false
 * </pre>
 */
@ConfigurationProperties(prefix = "scanrest")
public class ScanRestProperties {

    /**
     * Enable or disable ScanRest entirely.
     */
    private boolean enabled = true;

    /**
     * Name of the YAML test specification file.
     * Resolved from: 1) classpath (src/test/resources/)  2) project root.
     */
    private String file = "scanrest-tests.yml";

    /**
     * Execution mode: 'live' or 'embedded'.
     */
    private Mode mode = Mode.EMBEDDED;

    /**
     * Active profile (overrides config.activeProfile from YAML).
     */
    private String profile;

    /**
     * Base URL override for live mode.
     */
    private String baseUrl;

    /**
     * Whether to auto-scan controllers and generate the YAML skeleton if the file doesn't exist.
     */
    private boolean autoGenerate = false;

    /**
     * Whether to fail the build/test phase when tests fail.
     */
    private boolean failOnError = true;

    /**
     * Path to write a test report file.
     */
    private String reportPath;

    /**
     * Run tests automatically on application startup (for test phase).
     */
    private boolean runOnStartup = false;

    public enum Mode {
        LIVE, EMBEDDED
    }

    // Getters and setters

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isAutoGenerate() { return autoGenerate; }
    public void setAutoGenerate(boolean autoGenerate) { this.autoGenerate = autoGenerate; }

    public boolean isFailOnError() { return failOnError; }
    public void setFailOnError(boolean failOnError) { this.failOnError = failOnError; }

    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }

    public boolean isRunOnStartup() { return runOnStartup; }
    public void setRunOnStartup(boolean runOnStartup) { this.runOnStartup = runOnStartup; }
}
