package io.scanrest.cli;

import io.scanrest.generator.YamlGenerator;
import io.scanrest.model.ScannedEndpoint;
import io.scanrest.scanner.EndpointScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI command to scan endpoints and generate a skeleton YAML test file.
 */
@Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        description = "Scan endpoints and generate a skeleton YAML test specification file."
)
public class GenerateCommand implements Runnable {

    @Option(names = {"-c", "--classpath"}, required = true,
            description = "Path to compiled classes directory or JAR")
    private String classpath;

    @Option(names = {"-o", "--output"}, defaultValue = "scanrest-tests.yml",
            description = "Output YAML file path (default: scanrest-tests.yml)")
    private String output;

    @Option(names = {"-n", "--name"}, defaultValue = "API Tests",
            description = "Test suite name")
    private String suiteName;

    @Option(names = {"-b", "--base-url"}, defaultValue = "http://localhost:8080",
            description = "Base URL for the API (default: http://localhost:8080)")
    private String baseUrl;

    @Override
    public void run() {
        EndpointScanner scanner = new EndpointScanner();
        List<ScannedEndpoint> endpoints = scanner.scan(classpath);

        if (endpoints.isEmpty()) {
            System.out.println("No endpoints found. Cannot generate YAML.");
            return;
        }

        try {
            YamlGenerator generator = new YamlGenerator();
            generator.generate(endpoints, Path.of(output), suiteName, baseUrl);
            System.out.printf("Generated YAML test file at: %s (%d endpoints)%n", output, endpoints.size());
            System.out.println("Edit the file to add request bodies and expected responses, then run:");
            System.out.printf("  java -jar scanrest.jar test --file %s%n", output);
        } catch (Exception e) {
            System.err.println("Failed to generate YAML: " + e.getMessage());
        }
    }
}
