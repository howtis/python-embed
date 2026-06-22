package com.example.pythonembed.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Parses Python requirements.txt files into a list of package specifications.
 *
 * <p>Each non-empty, non-comment line is treated as a package spec.
 * Advanced pip options ({@code -r}, {@code -c}, {@code --index-url}, etc.)
 * are skipped with a warning.
 */
public final class RequirementsParser {

    private static final Logger logger = Logger.getLogger(RequirementsParser.class.getName());

    private RequirementsParser() {
    }

    /**
     * Parses a requirements.txt file and returns the list of package specs.
     *
     * @param file path to the requirements.txt file
     * @return list of package specification strings (may be empty)
     * @throws IOException if the file cannot be read
     */
    public static List<String> parse(Path file) throws IOException {
        List<String> packages = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);

        for (String rawLine : lines) {
            String line = rawLine.trim();

            // Skip empty lines
            if (line.isEmpty()) {
                continue;
            }

            // Skip comment lines
            if (line.startsWith("#")) {
                continue;
            }

            // Skip pip option lines (-r, -c, --*, etc.)
            if (line.startsWith("-")) {
                logger.warning("Skipping unsupported pip option: " + line);
                continue;
            }

            packages.add(line);
        }

        return packages;
    }
}
