package com.chatapp.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NoDirectConsoleOutputTest {

    @Test
    void productionSourcesDoNotWriteDirectlyToStandardOutputOrError() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");

        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> assertNoDirectConsoleWrites(path));
        }
    }

    private static void assertNoDirectConsoleWrites(Path path) {
        final String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read source file: " + path, e);
        }

        assertFalse(source.contains("System.out"),
                () -> "Direct System.out usage found in " + path);
        assertFalse(source.contains("System.err"),
                () -> "Direct System.err usage found in " + path);
    }
}
