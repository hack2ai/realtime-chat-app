package com.chatapp.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionCodePolicyTest {
    private static final Path PRODUCTION_SOURCE = Path.of("src", "main", "java");

    private static final Pattern BROAD_EXCEPTION_CATCH = Pattern.compile(
            "\\bcatch\\s*\\(\\s*(?:java\\.lang\\.)?(?:Exception|Throwable)\\b");
    private static final Pattern DIRECT_CONSOLE_OUTPUT = Pattern.compile(
            "\\bSystem\\.(?:out|err)\\.");
    private static final Pattern STACK_TRACE_PRINT = Pattern.compile(
            "\\bprintStackTrace\\s*\\(");
    private static final Pattern UNBOUNDED_CACHED_EXECUTOR = Pattern.compile(
            "\\bExecutors\\.newCachedThreadPool\\s*\\(");

    @Test
    void productionCodeHasNoBroadExceptionCatchBlocks() throws IOException {
        List<String> violations = findViolations(BROAD_EXCEPTION_CATCH);
        assertTrue(violations.isEmpty(), () -> formatViolations(
                "Avoid broad Exception/Throwable catch blocks in production code", violations));
    }

    @Test
    void productionCodeUsesStructuredLogging() throws IOException {
        List<String> violations = findViolations(DIRECT_CONSOLE_OUTPUT);
        violations.addAll(findViolations(STACK_TRACE_PRINT));
        assertTrue(violations.isEmpty(), () -> formatViolations(
                "Use the application logging API instead of direct console output or stack-trace printing",
                violations));
    }

    @Test
    void productionCodeDoesNotCreateUnboundedCachedExecutors() throws IOException {
        List<String> violations = findViolations(UNBOUNDED_CACHED_EXECUTOR);
        assertTrue(violations.isEmpty(), () -> formatViolations(
                "Use bounded executors or virtual threads instead of cached thread pools", violations));
    }

    private static List<String> findViolations(Pattern pattern) throws IOException {
        try (Stream<Path> paths = Files.walk(PRODUCTION_SOURCE)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readViolatingLines(path, pattern).stream())
                    .toList();
        }
    }

    private static List<String> readViolatingLines(Path path, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(index -> pattern.matcher(lines.get(index)).find())
                    .mapToObj(index -> path + ":" + (index + 1) + ": " + lines.get(index).trim())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect production source: " + path, e);
        }
    }

    private static String formatViolations(String message, List<String> violations) {
        return message + ":" + System.lineSeparator() + String.join(System.lineSeparator(), violations);
    }
}
