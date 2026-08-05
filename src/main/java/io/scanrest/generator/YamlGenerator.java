package io.scanrest.generator;

import io.scanrest.model.HttpMethod;
import io.scanrest.model.ScannedEndpoint;
import io.scanrest.model.ScannedEndpoint.ParameterInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates a skeleton YAML test specification file in the new ScanRest format
 * from scanned Spring endpoints.
 */
public class YamlGenerator {

    private static final Logger log = LoggerFactory.getLogger(YamlGenerator.class);

    public void generate(List<ScannedEndpoint> endpoints, Path outputPath,
                          String suiteName, String baseUrl) throws IOException {
        StringBuilder yaml = new StringBuilder();

        yaml.append("# ============================================\n");
        yaml.append("# ScanRest Test Specification (auto-generated)\n");
        yaml.append("# ============================================\n\n");

        // Config block
        yaml.append("config:\n");
        yaml.append("  name: \"%s\"\n".formatted(suiteName));
        yaml.append("  baseUrl: \"%s\"\n\n".formatted(baseUrl));

        yaml.append("  # Uncomment to use profiles:\n");
        yaml.append("  # activeProfile: dev\n");
        yaml.append("  # profiles:\n");
        yaml.append("  #   dev:\n");
        yaml.append("  #     baseUrl: \"http://localhost:8080\"\n");
        yaml.append("  #     token: \"dev-token\"\n");
        yaml.append("  #   staging:\n");
        yaml.append("  #     baseUrl: \"https://staging.api.com\"\n");
        yaml.append("  #     token: \"staging-token\"\n\n");

        yaml.append("  globalHeaders:\n");
        yaml.append("    Content-Type: \"application/json\"\n");
        yaml.append("    # Authorization: \"Bearer {{token}}\"\n\n");

        yaml.append("  variables: {}\n");
        yaml.append("    # userId: \"101\"\n\n");

        // Tests block
        yaml.append("# ============================================\n");
        yaml.append("# TESTS\n");
        yaml.append("# ============================================\n");
        yaml.append("tests:\n");

        for (ScannedEndpoint endpoint : endpoints) {
            yaml.append("\n");
            yaml.append("  # %s#%s\n".formatted(endpoint.getClassName(), endpoint.getMethodName()));

            List<ParameterInfo> pathVars = endpoint.getParameters().stream()
                    .filter(p -> p.getType() == ParameterInfo.Type.PATH_VARIABLE).toList();
            List<ParameterInfo> queryParams = endpoint.getParameters().stream()
                    .filter(p -> p.getType() == ParameterInfo.Type.QUERY_PARAM).toList();
            boolean hasRequestBody = endpoint.getParameters().stream()
                    .anyMatch(p -> p.getType() == ParameterInfo.Type.REQUEST_BODY);

            boolean isSimple = pathVars.isEmpty() && queryParams.isEmpty() && !hasRequestBody
                    && (endpoint.getHttpMethod() == HttpMethod.GET || endpoint.getHttpMethod() == HttpMethod.DELETE);

            if (isSimple) {
                // Simple format for GET/DELETE without params
                yaml.append("  - %s %s:\n".formatted(endpoint.getHttpMethod(), endpoint.getPath()));
                yaml.append("      expect: 200\n");
            } else {
                // Detailed format
                yaml.append("  - name: \"%s %s\"\n".formatted(endpoint.getHttpMethod(), endpoint.getPath()));
                yaml.append("    path: %s\n".formatted(endpoint.getPath()));
                yaml.append("    method: %s\n".formatted(endpoint.getHttpMethod()));

                if (!queryParams.isEmpty() || hasRequestBody) {
                    yaml.append("    request:\n");
                    if (!queryParams.isEmpty()) {
                        yaml.append("      queryParams:\n");
                        for (ParameterInfo qp : queryParams) {
                            yaml.append("        %s: \"TODO\"\n".formatted(qp.getName()));
                        }
                    }
                    if (hasRequestBody) {
                        yaml.append("      body:\n");
                        yaml.append("        TODO: \"fill in request body fields\"\n");
                    }
                }

                yaml.append("    expect:\n");
                yaml.append("      status: 200\n");
                yaml.append("      body:\n");
                yaml.append("        $.TODO: NOT_NULL\n");
            }
        }

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, yaml.toString());
        log.info("Generated test YAML at: {}", outputPath);
    }
}
