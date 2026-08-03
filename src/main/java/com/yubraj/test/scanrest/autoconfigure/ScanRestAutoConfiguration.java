package com.yubraj.test.scanrest.autoconfigure;

import com.yubraj.test.scanrest.config.YamlParser;
import com.yubraj.test.scanrest.engine.AssertionEngine;
import com.yubraj.test.scanrest.engine.VariableResolver;
import com.yubraj.test.scanrest.executor.LiveHttpExecutor;
import com.yubraj.test.scanrest.executor.TestExecutor;
import com.yubraj.test.scanrest.generator.YamlGenerator;
import com.yubraj.test.scanrest.report.TestReporter;
import com.yubraj.test.scanrest.scanner.EndpointScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for ScanRest.
 *
 * <p>Activated when {@code scanrest.enabled=true} (default).
 * Registers all ScanRest beans into the Spring context so they can be
 * injected into tests or used by the {@link ScanRestTestRunner}.</p>
 *
 * <p>Usage in {@code application.properties}:</p>
 * <pre>
 * scanrest.enabled=true
 * scanrest.file=scanrest-tests.yml
 * scanrest.mode=embedded
 * scanrest.profile=dev
 * scanrest.fail-on-error=true
 * </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(ScanRestProperties.class)
@ConditionalOnProperty(prefix = "scanrest", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScanRestAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScanRestAutoConfiguration.class);

    @Bean
    public YamlParser scanRestYamlParser() {
        return new YamlParser();
    }

    @Bean
    public AssertionEngine scanRestAssertionEngine() {
        return new AssertionEngine();
    }

    @Bean
    public EndpointScanner scanRestEndpointScanner() {
        return new EndpointScanner();
    }

    @Bean
    public YamlGenerator scanRestYamlGenerator() {
        return new YamlGenerator();
    }

    @Bean
    public TestReporter scanRestTestReporter() {
        return new TestReporter();
    }

    @Bean
    public ScanRestTestRunner scanRestTestRunner(ScanRestProperties properties,
                                                  YamlParser yamlParser,
                                                  EndpointScanner endpointScanner,
                                                  YamlGenerator yamlGenerator,
                                                  TestReporter testReporter) {
        return new ScanRestTestRunner(properties, yamlParser, endpointScanner, yamlGenerator, testReporter);
    }
}
