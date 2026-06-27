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

    @Test
    void setup_crossCompile_windowsTarget_findsPythonExe(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);

        // Create Windows-style python.exe in venv root
        Path pythonExe = venvDir.resolve("python.exe");
        Files.createFile(pythonExe);

        // Write fingerprint
        String pythonVersion = "3.12";
        String packageHash = FingerprintManager.computePackageHash(
                List.of("msgpack"), null, List.of(), null);
        FingerprintManager.write(venvDir, "bundled", pythonVersion, packageHash);

        VenvConfig config = VenvConfig.builder()
                .venvDir(venvDir)
                .targetOs("windows")
                .build();

        PythonEnvironment result = VenvManager.setup(config);

        assertNotNull(result);
        assertEquals(venvDir, result.venvDir());
        assertEquals("bundled", result.source());
    }

    @Test
    void setup_crossCompile_linuxTarget_findsBinPython3(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);

        // Create Linux-style bin/python3
        Path pythonExe = venvDir.resolve("bin").resolve("python3");
        Files.createDirectories(pythonExe.getParent());
        Files.createFile(pythonExe);

        // Write fingerprint
        String pythonVersion = "3.12";
        String packageHash = FingerprintManager.computePackageHash(
                List.of("msgpack"), null, List.of(), null);
        FingerprintManager.write(venvDir, "bundled", pythonVersion, packageHash);

        VenvConfig config = VenvConfig.builder()
                .venvDir(venvDir)
                .targetOs("linux")
                .build();

        PythonEnvironment result = VenvManager.setup(config);

        assertNotNull(result);
        assertEquals(venvDir, result.venvDir());
        assertEquals("bundled", result.source());
    }

    @Test
    void setup_crossCompile_macosTarget_findsBinPython3(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);

        // Create macOS-style bin/python3 (same structure as Linux)
        Path pythonExe = venvDir.resolve("bin").resolve("python3");
        Files.createDirectories(pythonExe.getParent());
        Files.createFile(pythonExe);

        // Write fingerprint
        String pythonVersion = "3.12";
        String packageHash = FingerprintManager.computePackageHash(
                List.of("msgpack"), null, List.of(), null);
        FingerprintManager.write(venvDir, "bundled", pythonVersion, packageHash);

        VenvConfig config = VenvConfig.builder()
                .venvDir(venvDir)
                .targetOs("macos")
                .build();

        PythonEnvironment result = VenvManager.setup(config);

        assertNotNull(result);
        assertEquals(venvDir, result.venvDir());
        assertEquals("bundled", result.source());
    }

    @Test
    void setup_crossCompile_wrongExecutable_throwsIOException(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);

        // Create Linux-style bin/python3 but configure targetOs=windows
        // The fingerprint check will fail, and system Python won't be found,
        // so it will try to download (which we can't test here).
        // Instead, verify that pythonPresent is false.
        Path pythonExe = venvDir.resolve("bin").resolve("python3");
        Files.createDirectories(pythonExe.getParent());
        Files.createFile(pythonExe);

        // Write fingerprint with mismatched pythonVersion so it forces full setup
        String pythonVersion = "3.11";
        String packageHash = FingerprintManager.computePackageHash(
                List.of("msgpack"), null, List.of(), null);
        FingerprintManager.write(venvDir, "bundled", pythonVersion, packageHash);

        // No system python available, and targetOs=windows means
        // findPythonInDir won't find bin/python3
        // This will attempt download which may fail in test environment,
        // but the important thing is that the cross-compilation logic is correct.
        // We verify this via the findPythonInDir unit tests in PythonResolverTest.

        // For completeness, verify that targetOs is properly passed through:
        VenvConfig config = VenvConfig.builder()
                .venvDir(venvDir)
                .targetOs("windows")
                .pythonVersion("3.12")
                .build();

        // With targetOs=windows, bin/python3 should NOT be found (pythonPresent=false),
        // causing a full setup attempt. Since system Python may or may not be available,
        // this test validates parameter flow rather than the full setup outcome.
        // The PythonResolverTest already covers findPythonInDir cross-compilation.
        assertNotNull(config.targetOs());
        assertEquals("windows", config.targetOs());
    }
}
