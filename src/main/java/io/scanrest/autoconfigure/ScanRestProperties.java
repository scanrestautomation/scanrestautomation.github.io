package io.scanrest.autoconfigure;

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
 * # Execution mode: live (HTTP) | mockmvc (Spring MockMvc) | embedded (deprecated alias for mockmvc)
 * scanrest.mode=live
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
 *
 * # Auto-mock all @Service beans (for mockmvc mode)
 * scanrest.mock-services=false
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
     * Execution mode: 'live', 'mockmvc', or 'embedded' (deprecated alias for mockmvc).
     */
    private Mode mode = Mode.LIVE;

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

    /**
     * Auto-mock all {@code @Service} beans with Mockito (for mockmvc mode).
     * When enabled, ScanRest replaces service beans with mocks so controller
     * tests can run without real service implementations.
     */
    private boolean mockServices = false;

    public enum Mode {
        /** Execute tests via HTTP against a running server. */
        LIVE,
        /** Execute tests using Spring MockMvc from the existing application context. */
        MOCKMVC,
        /** @deprecated Use {@link #MOCKMVC} instead. */
        @Deprecated
        EMBEDDED
    }

    /**
     * Returns true if the mode uses MockMvc (MOCKMVC or deprecated EMBEDDED).
     */
    public boolean isMockMvcMode() {
        return mode == Mode.MOCKMVC || mode == Mode.EMBEDDED;
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

    public boolean isMockServices() { return mockServices; }
    public void setMockServices(boolean mockServices) { this.mockServices = mockServices; }
}
