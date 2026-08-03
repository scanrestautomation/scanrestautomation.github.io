package com.yubraj.test.scanrest.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI entry point for ScanRest.
 *
 * Usage:
 *   java -jar scanrest.jar scan --classpath target/classes
 *   java -jar scanrest.jar generate --classpath target/classes --output api-tests.yml
 *   java -jar scanrest.jar test --file api-tests.yml --mode live
 *   java -jar scanrest.jar test --file api-tests.yml --mode embedded --app-class com.example.Application
 */
@Command(
        name = "scanrest",
        version = "ScanRest 1.0-SNAPSHOT",
        mixinStandardHelpOptions = true,
        description = "ScanRest - Auto API testing tool for Spring Boot projects. Scans endpoints, generates YAML test specs, and executes tests.",
        subcommands = {
                ScanCommand.class,
                GenerateCommand.class,
                TestCommand.class
        }
)
public class ScanRestCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ScanRestCli()).execute(args);
        System.exit(exitCode);
    }
}
