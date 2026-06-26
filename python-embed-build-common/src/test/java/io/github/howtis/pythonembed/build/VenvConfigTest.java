package io.github.howtis.pythonembed.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class VenvConfigTest {

    @Test
    void builder_minimalConfig_returnsDefaults(@TempDir Path tempDir) {
        Path venvPath = tempDir.resolve("venv");
        VenvConfig config = VenvConfig.builder()
                .venvDir(venvPath)
                .build();

        assertNotNull(config.packages());
        assertTrue(config.packages().isEmpty());
        assertEquals("3.12", config.pythonVersion());
        assertNull(config.requirementsFile());
        assertNull(config.pyprojectTomlFile());
        assertNull(config.pipIndexUrl());
        assertNotNull(config.pipExtraArgs());
        assertTrue(config.pipExtraArgs().isEmpty());
        assertEquals(venvPath, config.venvDir());
        assertNull(config.targetOs());
    }

    @Test
    void builder_fullConfig_setsAllFields(@TempDir Path tempDir) {
        Consumer<String> logger = msg -> {};
        Path requirementsFile = Path.of("requirements.txt");
        Path pyprojectTomlFile = Path.of("pyproject.toml");
        Path venvDir = tempDir.resolve("venv");

        VenvConfig config = VenvConfig.builder()
                .packages(List.of("numpy", "pandas"))
                .pythonVersion("3.11")
                .requirementsFile(requirementsFile)
                .pyprojectTomlFile(pyprojectTomlFile)
                .pipIndexUrl("https://example.com/simple")
                .pipExtraArgs(List.of("--extra-index-url", "https://other.com"))
                .venvDir(venvDir)
                .targetOs("linux")
                .logger(logger)
                .build();

        assertEquals(List.of("numpy", "pandas"), config.packages());
        assertEquals("3.11", config.pythonVersion());
        assertEquals(requirementsFile, config.requirementsFile());
        assertEquals(pyprojectTomlFile, config.pyprojectTomlFile());
        assertEquals("https://example.com/simple", config.pipIndexUrl());
        assertEquals(List.of("--extra-index-url", "https://other.com"), config.pipExtraArgs());
        assertEquals(venvDir, config.venvDir());
        assertEquals("linux", config.targetOs());
        assertSame(logger, config.logger());
    }

    @Test
    void config_isImmutable_copyDefensive(@TempDir Path tempDir) {
        List<String> packages = new java.util.ArrayList<>(List.of("numpy"));
        VenvConfig config = VenvConfig.builder()
                .packages(packages)
                .venvDir(tempDir.resolve("venv"))
                .build();

        packages.add("pandas");
        assertEquals(List.of("numpy"), config.packages());
    }
}
