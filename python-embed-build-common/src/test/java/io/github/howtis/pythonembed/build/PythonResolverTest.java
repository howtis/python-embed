package io.github.howtis.pythonembed.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PythonResolverTest {

    @Test
    void findPythonInDir_windowsTarget_findsExe(@TempDir Path tempDir) throws IOException {
        Path exe = tempDir.resolve("python.exe");
        Files.createFile(exe);

        Path result = PythonResolver.findPythonInDir(tempDir, "windows");
        assertEquals(exe, result);
    }

    @Test
    void findPythonInDir_windowsTarget_findsScriptsExe(@TempDir Path tempDir) throws IOException {
        Path exe = tempDir.resolve("Scripts").resolve("python.exe");
        Files.createDirectories(exe.getParent());
        Files.createFile(exe);

        Path result = PythonResolver.findPythonInDir(tempDir, "windows");
        assertEquals(exe, result);
    }

    @Test
    void findPythonInDir_linuxTarget_findsBinPython3(@TempDir Path tempDir) throws IOException {
        Path bin = tempDir.resolve("bin").resolve("python3");
        Files.createDirectories(bin.getParent());
        Files.createFile(bin);

        Path result = PythonResolver.findPythonInDir(tempDir, "linux");
        assertEquals(bin, result);
    }

    @Test
    void findPythonInDir_linuxTarget_findsBinPython(@TempDir Path tempDir) throws IOException {
        Path bin = tempDir.resolve("bin").resolve("python");
        Files.createDirectories(bin.getParent());
        Files.createFile(bin);

        Path result = PythonResolver.findPythonInDir(tempDir, "linux");
        assertEquals(bin, result);
    }

    @Test
    void findPythonInDir_macosTarget_findsBinPython3(@TempDir Path tempDir) throws IOException {
        Path bin = tempDir.resolve("bin").resolve("python3");
        Files.createDirectories(bin.getParent());
        Files.createFile(bin);

        Path result = PythonResolver.findPythonInDir(tempDir, "macos");
        assertEquals(bin, result);
    }

    @Test
    void findPythonInDir_windowsTarget_noExe_returnsNull(@TempDir Path tempDir) throws IOException {
        Path result = PythonResolver.findPythonInDir(tempDir, "windows");
        assertNull(result);
    }

    @Test
    void findPythonInDir_linuxTarget_noExe_returnsNull(@TempDir Path tempDir) throws IOException {
        Path result = PythonResolver.findPythonInDir(tempDir, "linux");
        assertNull(result);
    }

    @Test
    void findPythonInDir_missingDir_returnsNull() throws IOException {
        Path nonExistent = Path.of("nonexistent_dir_for_test");
        Path result = PythonResolver.findPythonInDir(nonExistent, "windows");
        assertNull(result);
    }

    @Test
    void resolveTargetOs_returnsConfiguredValue() {
        assertEquals("windows", PythonResolver.resolveTargetOs("Windows"));
        assertEquals("linux", PythonResolver.resolveTargetOs("LINUX"));
        assertEquals("macos", PythonResolver.resolveTargetOs("MacOS"));
    }

    @Test
    void resolveTargetOs_nullUsesCurrentOs() {
        String result = PythonResolver.resolveTargetOs(null);
        assertTrue(result.equals("windows") || result.equals("linux") || result.equals("macos"));
    }

    @Test
    void resolveTargetOs_emptyUsesCurrentOs() {
        String result = PythonResolver.resolveTargetOs("");
        assertTrue(result.equals("windows") || result.equals("linux") || result.equals("macos"));
    }

    @Test
    void resolveVenvPython_windows_returnsScriptsPythonExe() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        Path venvDir = Path.of("/tmp/venv");
        Path result = PythonResolver.resolveVenvPython(venvDir);
        if (osName.contains("win")) {
            assertEquals(venvDir.resolve("Scripts/python.exe"), result);
        } else {
            assertEquals(venvDir.resolve("bin/python3"), result);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void isWindows_returnsTrueOnWindows() {
        assertTrue(PythonResolver.isWindows());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void isWindows_returnsFalseOnNonWindows() {
        assertFalse(PythonResolver.isWindows());
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void isMac_returnsTrueOnMac() {
        assertTrue(PythonResolver.isMac());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.WINDOWS})
    void isMac_returnsFalseOnNonMac() {
        assertFalse(PythonResolver.isMac());
    }

    @Test
    void detectTargetTriple_returnsValidTriple() {
        String triple = PythonResolver.detectTargetTriple();
        assertNotNull(triple);
        assertTrue(triple.matches(".+-.+-.+"));
    }
}
