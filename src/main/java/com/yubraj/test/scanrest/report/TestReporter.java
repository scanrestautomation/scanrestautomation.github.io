package com.yubraj.test.scanrest.report;

import com.yubraj.test.scanrest.model.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates test execution reports in console and file formats.
 */
public class TestReporter {

    private static final Logger log = LoggerFactory.getLogger(TestReporter.class);

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";

    /**
     * Print a formatted report to the console.
     */
    public void printConsoleReport(List<TestResult> results, PrintStream out) {
        out.println();
        out.println(ANSI_BOLD + "=" .repeat(80) + ANSI_RESET);
        out.println(ANSI_BOLD + "  SCANREST RESULTS" + ANSI_RESET);
        out.println(ANSI_BOLD + "=" .repeat(80) + ANSI_RESET);
        out.println();

        long passed = results.stream().filter(r -> r.getStatus() == TestResult.Status.PASSED).count();
        long failed = results.stream().filter(r -> r.getStatus() == TestResult.Status.FAILED).count();
        long errors = results.stream().filter(r -> r.getStatus() == TestResult.Status.ERROR).count();
        Duration totalDuration = results.stream()
                .map(TestResult::getDuration)
                .filter(d -> d != null)
                .reduce(Duration.ZERO, Duration::plus);

        for (TestResult result : results) {
            String statusIcon = switch (result.getStatus()) {
                case PASSED -> ANSI_GREEN + "PASS" + ANSI_RESET;
                case FAILED -> ANSI_RED + "FAIL" + ANSI_RESET;
                case ERROR -> ANSI_YELLOW + "ERR " + ANSI_RESET;
            };

            String duration = result.getDuration() != null
                    ? " (%dms)".formatted(result.getDuration().toMillis()) : "";

            out.printf("  [%s] %s %s - %s%s%n",
                    statusIcon,
                    result.getHttpMethod(),
                    result.getEndpointPath(),
                    result.getTestCaseName(),
                    duration);

            if (result.getStatus() == TestResult.Status.FAILED) {
                for (String failure : result.getFailures()) {
                    out.printf("         %s> %s%s%n", ANSI_RED, failure, ANSI_RESET);
                }
            }

            if (result.getStatus() == TestResult.Status.ERROR) {
                out.printf("         %s> %s%s%n", ANSI_YELLOW, result.getErrorMessage(), ANSI_RESET);
            }
        }

        out.println();
        out.println("-".repeat(80));
        out.printf("  Total: %d  |  %sPassed: %d%s  |  %sFailed: %d%s  |  %sErrors: %d%s  |  Time: %dms%n",
                results.size(),
                ANSI_GREEN, passed, ANSI_RESET,
                ANSI_RED, failed, ANSI_RESET,
                ANSI_YELLOW, errors, ANSI_RESET,
                totalDuration.toMillis());
        out.println("-".repeat(80));

        if (failed == 0 && errors == 0) {
            out.println(ANSI_GREEN + ANSI_BOLD + "  ALL TESTS PASSED!" + ANSI_RESET);
        } else {
            out.println(ANSI_RED + ANSI_BOLD + "  SOME TESTS FAILED!" + ANSI_RESET);
        }
        out.println();
    }

    /**
     * Write a report to a file.
     */
    public void writeFileReport(List<TestResult> results, Path outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        sb.append("ScanRest Report\n");
        sb.append("Generated: %s\n".formatted(timestamp));
        sb.append("=".repeat(80)).append("\n\n");

        long passed = results.stream().filter(r -> r.getStatus() == TestResult.Status.PASSED).count();
        long failed = results.stream().filter(r -> r.getStatus() == TestResult.Status.FAILED).count();
        long errors = results.stream().filter(r -> r.getStatus() == TestResult.Status.ERROR).count();

        sb.append("Summary: Total=%d, Passed=%d, Failed=%d, Errors=%d\n\n".formatted(
                results.size(), passed, failed, errors));

        for (TestResult result : results) {
            sb.append("[%s] %s %s - %s".formatted(
                    result.getStatus(),
                    result.getHttpMethod(),
                    result.getEndpointPath(),
                    result.getTestCaseName()));

            if (result.getDuration() != null) {
                sb.append(" (%dms)".formatted(result.getDuration().toMillis()));
            }
            sb.append("\n");

            if (result.getStatus() == TestResult.Status.FAILED) {
                for (String failure : result.getFailures()) {
                    sb.append("  > ").append(failure).append("\n");
                }
            }
            if (result.getStatus() == TestResult.Status.ERROR) {
                sb.append("  > ").append(result.getErrorMessage()).append("\n");
            }
            sb.append("\n");
        }

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, sb.toString());
        log.info("Report written to: {}", outputPath);
    }

    /**
     * @return true if all tests passed
     */
    public boolean allPassed(List<TestResult> results) {
        return results.stream().allMatch(TestResult::isPassed);
    }
}
