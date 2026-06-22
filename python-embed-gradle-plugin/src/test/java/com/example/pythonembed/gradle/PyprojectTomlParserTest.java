package com.example.pythonembed.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PyprojectTomlParserTest {

    @Test
    void parse_basicDependencies_returnsAll(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                name = "myproject"
                dependencies = ["numpy>=1.26", "pandas"]
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("numpy>=1.26", "pandas"), result);
    }

    @Test
    void parse_multiLineArray_returnsAll(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                dependencies = [
                  "numpy>=1.26",
                  "pandas",
                  "torch==2.4.0",
                ]
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("numpy>=1.26", "pandas", "torch==2.4.0"), result);
    }

    @Test
    void parse_emptyArray_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                dependencies = []
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_noProjectSection_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [build-system]
                requires = ["setuptools"]
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_noDependenciesKey_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                name = "myproject"
                version = "1.0.0"
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_emptyFile_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, "");

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_multipleSections_ignoresOtherSections(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [build-system]
                requires = ["setuptools>=68"]

                [project]
                name = "myproject"
                dependencies = ["numpy>=1.26"]

                [tool.poetry]
                name = "something"
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("numpy>=1.26"), result);
    }

    @Test
    void parse_lastElementWithComma_handled(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                dependencies = ["numpy>=1.26", "pandas",]
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("numpy>=1.26", "pandas"), result);
    }

    @Test
    void parse_escapeSequence_basicString(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                dependencies = ["package\\"with\\"quotes"]
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("package\"with\"quotes"), result);
    }

    @Test
    void parse_literalString_noEscape(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                dependencies = ['numpy>=1.26', 'pandas']
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("numpy>=1.26", "pandas"), result);
    }

    @Test
    void parse_packageWithExtras_preserved(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, """
                [project]
                dependencies = ["tensorflow[and-cuda]>=2.16.0"]
                """);

        List<String> result = PyprojectTomlParser.parse(tomlFile);

        assertEquals(List.of("tensorflow[and-cuda]>=2.16.0"), result);
    }

    @Test
    void parse_fileNotFound_throwsIOException() {
        Path nonExistent = Path.of("nonexistent.toml");
        assertThrows(IOException.class, () -> PyprojectTomlParser.parse(nonExistent));
    }
}
