package io.scanrest.cli;

import io.scanrest.config.YamlParser;
import io.scanrest.engine.VariableResolver;
import io.scanrest.executor.EmbeddedSpringExecutor;
import io.scanrest.executor.LiveHttpExecutor;
import io.scanrest.executor.TestExecutor;
import io.scanrest.model.TestResult;
import io.scanrest.model.TestSuiteSpec;
import io.scanrest.report.TestReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI command to execute API tests from a YAML specification file.
 */
@Command(
        name = "test",
        mixinStandardHelpOptions = true,
        description = "Execute API tests defined in a YAML specification file."
)
public class TestCommand implements Runnable {

    public enum Mode { live, embedded }

    @Option(names = {"-f", "--file"}, required = true,
            description = "Path to the YAML test specification file")
    private String yamlFile;

    @Option(names = {"-m", "--mode"}, defaultValue = "live",
            description = "Execution mode: 'live' (HTTP against running server) or 'embedded' (MockMvc). Default: live")
    private Mode mode;

    @Option(names = {"--app-class"},
            description = "Fully qualified Spring Boot application class (required for embedded mode)")
    private String appClass;

    @Option(names = {"-p", "--profile"},
            description = "Active profile to use (overrides config.activeProfile)")
    private String profile;

    @Option(names = {"--report"},
            description = "Path to write a test report file")
    private String reportPath;

    @Override
    public void run() {
        try {
            // 1. Parse YAML
            YamlParser parser = new YamlParser();
            TestSuiteSpec suite = parser.parse(Path.of(yamlFile));

            // Override profile from CLI if provided
            if (profile != null && suite.getConfig() != null) {
                suite.getConfig().setActiveProfile(profile);
            }

            System.out.printf("Loaded test suite '%s' with %d tests%n%n",
                    suite.getConfig().getName(), suite.getTests().size());

            if (profile != null) {
                System.out.printf("Active profile: %s%n", profile);
            }

            // 2. Build variable resolver
            VariableResolver resolver = new VariableResolver(suite.getConfig().resolveVariables());

            // 3. Create executor
            TestExecutor executor = createExecutor();

            // 4. Execute tests
            List<TestResult> results = executor.execute(suite, resolver);

            // 5. Report
            TestReporter reporter = new TestReporter();
            reporter.printConsoleReport(results, System.out);

            if (reportPath != null) {
                reporter.writeFileReport(results, Path.of(reportPath));
                System.out.println("Report written to: " + reportPath);
            }

            // 6. Exit code
            if (!reporter.allPassed(results)) {
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private TestExecutor createExecutor() throws Exception {
        return switch (mode) {
            case live -> new LiveHttpExecutor(Path.of(yamlFile).getParent() != null
                    ? Path.of(yamlFile).getParent() : Path.of("."));
            case embedded -> {
                if (appClass == null || appClass.isBlank()) {
                    throw new IllegalArgumentException(
                            "Embedded mode requires --app-class (e.g., com.example.Application)");
                }
                Class<?> clazz = Class.forName(appClass);
                yield new EmbeddedSpringExecutor(clazz);
            }
        };
    }
}
