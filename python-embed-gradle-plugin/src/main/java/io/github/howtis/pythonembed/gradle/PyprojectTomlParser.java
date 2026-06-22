package io.github.howtis.pythonembed.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal TOML parser that extracts {@code [project] dependencies} from a
 * PEP 621 {@code pyproject.toml} file.
 *
 * <p>Only the {@code dependencies} array in the {@code [project]} section is
 * parsed. All other sections and keys are ignored. No external TOML library
 * is required.
 */
public final class PyprojectTomlParser {

    private PyprojectTomlParser() {
    }

    /**
     * Extracts dependencies from the {@code [project]} section of a pyproject.toml file.
     *
     * @param file path to the pyproject.toml file
     * @return list of dependency strings (may be empty if no {@code [project]} section
     *         or no {@code dependencies} key is found)
     * @throws IOException if the file cannot be read
     */
    public static List<String> parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        return parseLines(lines);
    }

    static List<String> parseLines(List<String> lines) {
        // Find [project] section
        int projectStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("[project]")) {
                projectStart = i + 1;
                break;
            }
        }

        if (projectStart < 0) {
            return List.of();
        }

        // Find next section start (any [xxx] line) to limit the search scope
        int sectionEnd = lines.size();
        for (int i = projectStart; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                sectionEnd = i;
                break;
            }
        }

        // Find dependencies key within [project] section
        int depLineIdx = -1;
        for (int i = projectStart; i < sectionEnd; i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("dependencies") && trimmed.contains("=")) {
                depLineIdx = i;
                break;
            }
        }

        if (depLineIdx < 0) {
            return List.of();
        }

        // Collect all text from dependencies = [ to closing ]
        StringBuilder arrayContent = new StringBuilder();
        boolean started = false;
        int bracketDepth = 0;

        for (int i = depLineIdx; i < sectionEnd; i++) {
            String line = lines.get(i);
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!started) {
                    if (c == '[') {
                        started = true;
                        bracketDepth = 1;
                    }
                } else {
                    if (c == '[') {
                        bracketDepth++;
                        arrayContent.append(c);
                    } else if (c == ']') {
                        bracketDepth--;
                        if (bracketDepth == 0) {
                            // End of array
                            return extractStrings(arrayContent.toString());
                        }
                        arrayContent.append(c);
                    } else {
                        arrayContent.append(c);
                    }
                }
            }
        }

        // If we never found the closing bracket, return empty
        return List.of();
    }

    /**
     * Extracts individual TOML string values from the content between array brackets.
     */
    static List<String> extractStrings(String content) {
        List<String> result = new ArrayList<>();
        int i = 0;
        int len = content.length();

        while (i < len) {
            char c = content.charAt(i);

            // Skip whitespace and commas
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',') {
                i++;
                continue;
            }

            // Start of a string
            if (c == '"' || c == '\'') {
                char quote = c;
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote

                while (i < len) {
                    char ch = content.charAt(i);
                    if (ch == '\\' && quote == '"') {
                        // Escape sequence (only processed for basic strings)
                        i++;
                        if (i < len) {
                            char escaped = content.charAt(i);
                            switch (escaped) {
                                case '"': sb.append('"'); break;
                                case '\\': sb.append('\\'); break;
                                case 'n': sb.append('\n'); break;
                                case 't': sb.append('\t'); break;
                                case 'r': sb.append('\r'); break;
                                default: sb.append('\\').append(escaped); break;
                            }
                            i++;
                        }
                    } else if (ch == quote) {
                        i++; // skip closing quote
                        break;
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }

                String value = sb.toString().trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            } else if (c == '#') {
                // Comment within array - skip to end of line
                while (i < len && content.charAt(i) != '\n') {
                    i++;
                }
            } else {
                // Unexpected character - skip
                i++;
            }
        }

        return result;
    }
}
