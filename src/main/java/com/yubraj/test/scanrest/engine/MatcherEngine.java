package com.yubraj.test.scanrest.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates matcher expressions used in expected response assertions.
 *
 * Supported matchers:
 * <ul>
 *   <li>{@code NOT_NULL} - value must exist and not be null</li>
 *   <li>{@code NULL} - value must be null</li>
 *   <li>{@code NOT_EMPTY} - string/array must not be empty</li>
 *   <li>{@code CONTAINS("substring")} - value must contain the substring</li>
 *   <li>{@code STARTS_WITH("prefix")} - value must start with prefix</li>
 *   <li>{@code ENDS_WITH("suffix")} - value must end with suffix</li>
 *   <li>{@code MATCHES("regex")} - value must match the regex</li>
 *   <li>{@code GT(number)} - value must be greater than number</li>
 *   <li>{@code LT(number)} - value must be less than number</li>
 *   <li>{@code GTE(number)} - value must be >= number</li>
 *   <li>{@code LTE(number)} - value must be <= number</li>
 *   <li>{@code SIZE(number)} - array/string must have this size/length</li>
 *   <li>Any other value is compared for equality (as string)</li>
 * </ul>
 */
public class MatcherEngine {

    private static final Pattern FUNC_PATTERN = Pattern.compile("^(\\w+)\\((.*)\\)$");

    /**
     * Evaluate whether the actual value matches the expected expression.
     *
     * @param expected the expected value or matcher expression (from YAML)
     * @param actual   the actual value (from response, extracted via JSONPath)
     * @return null if matched, otherwise an error description
     */
    public static String evaluate(Object expected, Object actual) {
        if (expected == null) return null; // no assertion

        String expr = String.valueOf(expected).trim();

        // Built-in keyword matchers
        return switch (expr) {
            case "NOT_NULL" -> actual != null ? null : "expected NOT_NULL but got null";
            case "NULL" -> actual == null ? null : "expected NULL but got: " + actual;
            case "NOT_EMPTY" -> evaluateNotEmpty(actual);
            default -> evaluateExpression(expr, actual);
        };
    }

    private static String evaluateExpression(String expr, Object actual) {
        Matcher m = FUNC_PATTERN.matcher(expr);
        if (m.matches()) {
            String func = m.group(1);
            String arg = m.group(2).trim();
            // Strip surrounding quotes if present
            if ((arg.startsWith("\"") && arg.endsWith("\"")) || (arg.startsWith("'") && arg.endsWith("'"))) {
                arg = arg.substring(1, arg.length() - 1);
            }
            return evaluateFunction(func, arg, actual);
        }

        // Plain equality check
        return evaluateEquality(expr, actual);
    }

    private static String evaluateFunction(String func, String arg, Object actual) {
        if (actual == null) {
            return "expected %s(%s) but got null".formatted(func, arg);
        }
        String actualStr = String.valueOf(actual);

        return switch (func) {
            case "CONTAINS" -> actualStr.contains(arg) ? null
                    : "expected CONTAINS(\"%s\") but got: %s".formatted(arg, actualStr);
            case "STARTS_WITH" -> actualStr.startsWith(arg) ? null
                    : "expected STARTS_WITH(\"%s\") but got: %s".formatted(arg, actualStr);
            case "ENDS_WITH" -> actualStr.endsWith(arg) ? null
                    : "expected ENDS_WITH(\"%s\") but got: %s".formatted(arg, actualStr);
            case "MATCHES" -> actualStr.matches(arg) ? null
                    : "expected MATCHES(\"%s\") but got: %s".formatted(arg, actualStr);
            case "GT" -> compareNumber(actual, arg, ">")
                    ? null : "expected GT(%s) but got: %s".formatted(arg, actual);
            case "LT" -> compareNumber(actual, arg, "<")
                    ? null : "expected LT(%s) but got: %s".formatted(arg, actual);
            case "GTE" -> compareNumber(actual, arg, ">=")
                    ? null : "expected GTE(%s) but got: %s".formatted(arg, actual);
            case "LTE" -> compareNumber(actual, arg, "<=")
                    ? null : "expected LTE(%s) but got: %s".formatted(arg, actual);
            case "SIZE" -> evaluateSize(actual, arg);
            default -> "unknown matcher function: " + func;
        };
    }

    private static String evaluateNotEmpty(Object actual) {
        if (actual == null) return "expected NOT_EMPTY but got null";
        String s = String.valueOf(actual);
        if (s.isEmpty() || s.equals("[]") || s.equals("{}")) {
            return "expected NOT_EMPTY but got: " + s;
        }
        return null;
    }

    private static String evaluateEquality(String expected, Object actual) {
        if (actual == null) {
            return "expected '%s' but got null".formatted(expected);
        }

        String actualStr = String.valueOf(actual);

        // Try numeric comparison first
        try {
            double expectedNum = Double.parseDouble(expected);
            double actualNum = Double.parseDouble(actualStr);
            if (Double.compare(expectedNum, actualNum) == 0) return null;
            // Also handle int comparison (101 == 101.0)
            if (Math.abs(expectedNum - actualNum) < 0.0001) return null;
        } catch (NumberFormatException ignored) {}

        // Boolean comparison
        if (expected.equalsIgnoreCase("true") || expected.equalsIgnoreCase("false")) {
            if (expected.equalsIgnoreCase(actualStr)) return null;
        }

        // String equality
        if (expected.equals(actualStr)) return null;

        return "expected '%s' but got '%s'".formatted(expected, actualStr);
    }

    private static boolean compareNumber(Object actual, String threshold, String op) {
        try {
            double actualNum = Double.parseDouble(String.valueOf(actual));
            double thresholdNum = Double.parseDouble(threshold);
            return switch (op) {
                case ">" -> actualNum > thresholdNum;
                case "<" -> actualNum < thresholdNum;
                case ">=" -> actualNum >= thresholdNum;
                case "<=" -> actualNum <= thresholdNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String evaluateSize(Object actual, String expectedSize) {
        try {
            int expected = Integer.parseInt(expectedSize);
            int actualSize;
            if (actual instanceof java.util.Collection<?> c) {
                actualSize = c.size();
            } else if (actual instanceof String s) {
                actualSize = s.length();
            } else {
                return "SIZE() requires array or string, got: " + actual.getClass().getSimpleName();
            }
            return actualSize == expected ? null
                    : "expected SIZE(%d) but got size %d".formatted(expected, actualSize);
        } catch (NumberFormatException e) {
            return "SIZE() argument must be integer: " + expectedSize;
        }
    }
}
