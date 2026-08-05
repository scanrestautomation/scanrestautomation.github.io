package io.scanrest.cli;

import io.scanrest.model.ScannedEndpoint;
import io.scanrest.scanner.EndpointScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * CLI command to scan Spring controllers and list discovered endpoints.
 */
@Command(
        name = "scan",
        mixinStandardHelpOptions = true,
        description = "Scan Spring controllers and list all discovered API endpoints."
)
public class ScanCommand implements Runnable {

    @Option(names = {"-c", "--classpath"}, required = true,
            description = "Path to compiled classes directory or JAR (e.g., target/classes)")
    private String classpath;

    @Override
    public void run() {
        EndpointScanner scanner = new EndpointScanner();
        List<ScannedEndpoint> endpoints = scanner.scan(classpath);

        if (endpoints.isEmpty()) {
            System.out.println("No endpoints found. Make sure the classpath points to compiled Spring Boot classes.");
            return;
        }

        System.out.println();
        System.out.printf("Found %d endpoints:%n%n", endpoints.size());
        System.out.printf("  %-8s %-40s %-30s %s%n", "METHOD", "PATH", "CLASS", "HANDLER");
        System.out.println("  " + "-".repeat(100));

        for (ScannedEndpoint ep : endpoints) {
            System.out.printf("  %-8s %-40s %-30s %s%n",
                    ep.getHttpMethod(), ep.getPath(), ep.getClassName(), ep.getMethodName());

            if (!ep.getParameters().isEmpty()) {
                for (ScannedEndpoint.ParameterInfo param : ep.getParameters()) {
                    System.out.printf("           -> %s: %s (%s)%n",
                            param.getType(), param.getName(), param.getTypeName());
                }
            }
        }
        System.out.println();
    }
}
