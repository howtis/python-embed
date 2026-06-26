package io.github.howtis.pythonembed.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class VenvManagerTest {

    @Test
    void runCommand_success() throws IOException {
        Consumer<String> log = msg -> {};
        assertDoesNotThrow(() -> VenvManager.runCommand(log, "java", "--version"));
    }

    @Test
    void runCommand_failure_throwsIOException() {
        Consumer<String> log = msg -> {};
        assertThrows(IOException.class,
                () -> VenvManager.runCommand(log, "java", "--nonexistent-flag-12345"));
    }

    @Test
    void setup_missingRequirementsFile_throwsIOException(@TempDir Path tempDir) {
        Path nonExistentReq = tempDir.resolve("nonexistent.txt");
        VenvConfig config = VenvConfig.builder()
                .venvDir(tempDir.resolve("venv"))
                .requirementsFile(nonExistentReq)
                .build();

        IOException ex = assertThrows(IOException.class, () -> VenvManager.setup(config));
        assertTrue(ex.getMessage().contains("requirements.txt not found"));
    }

    @Test
    void setup_missingPyprojectTomlFile_throwsIOException(@TempDir Path tempDir) {
        Path nonExistentPyproject = tempDir.resolve("nonexistent.toml");
        VenvConfig config = VenvConfig.builder()
                .venvDir(tempDir.resolve("venv"))
                .pyprojectTomlFile(nonExistentPyproject)
                .build();

        IOException ex = assertThrows(IOException.class, () -> VenvManager.setup(config));
        assertTrue(ex.getMessage().contains("pyproject.toml not found"));
    }

    @Test
    void setup_upToDate_skipsSetup(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);

        // Create a mock Python executable so findPythonInDir returns non-null
        Path pythonExe;
        if (PythonResolver.isWindows()) {
            pythonExe = venvDir.resolve("python.exe");
        } else {
            pythonExe = venvDir.resolve("bin").resolve("python3");
            Files.createDirectories(pythonExe.getParent());
        }
        Files.createFile(pythonExe);

        // Write a fingerprint matching the expected configuration.
        // includeMsgpack defaults to true, so msgpack is added to the packages list.
        String pythonVersion = "3.12";
        String packageHash = FingerprintManager.computePackageHash(
                List.of("msgpack"), null, List.of(), null);
        FingerprintManager.write(venvDir, "system", pythonVersion, packageHash);

        VenvConfig config = VenvConfig.builder()
                .venvDir(venvDir)
                .build();

        PythonEnvironment result = VenvManager.setup(config);

        assertNotNull(result);
        assertEquals(venvDir, result.venvDir());
        assertEquals("system", result.source());
    }

    @Test
    void setup_upToDate_withoutMsgpack(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);

        // Create a mock Python executable
        Path pythonExe;
        if (PythonResolver.isWindows()) {
            pythonExe = venvDir.resolve("python.exe");
        } else {
            pythonExe = venvDir.resolve("bin").resolve("python3");
            Files.createDirectories(pythonExe.getParent());
        }
        Files.createFile(pythonExe);

        // Write a fingerprint matching empty packages (msgpack excluded).
        String pythonVersion = "3.12";
        String packageHash = FingerprintManager.computePackageHash(
                List.of(), null, List.of(), null);
        FingerprintManager.write(venvDir, "system", pythonVersion, packageHash);

        VenvConfig config = VenvConfig.builder()
                .venvDir(venvDir)
                .includeMsgpack(false)
                .build();

        PythonEnvironment result = VenvManager.setup(config);

        assertNotNull(result);
        assertEquals(venvDir, result.venvDir());
        assertEquals("system", result.source());
    }
}
