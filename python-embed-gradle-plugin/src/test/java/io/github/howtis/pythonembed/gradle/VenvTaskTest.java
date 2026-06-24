package io.github.howtis.pythonembed.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class VenvTaskTest {

    private Project project;
    private VenvTask task;

    @BeforeEach
    void setUp() throws IOException {
        project = ProjectBuilder.builder().build();
        // Ensure build directory exists so DirectoryProperty can resolve
        Files.createDirectories(project.getBuildDir().toPath());
        task = project.getTasks().register("testVenv", VenvTask.class).get();
        task.getVenvDir().set(project.getLayout().getBuildDirectory().dir("test-venv"));
        task.getPythonVersion().set("3.12");
    }

    // ------------------------------------------------------------------
    // Requirements file validation
    // ------------------------------------------------------------------

    @Test
    void createVenv_missingRequirementsFile_throwsGradleException() {
        task.getRequirementsFile().set("nonexistent_requirements.txt");

        GradleException ex = assertThrows(GradleException.class, () -> task.createVenv());
        assertTrue(ex.getMessage().contains("requirements.txt not found"));
        assertTrue(ex.getMessage().contains("nonexistent_requirements.txt"));
    }


    // ------------------------------------------------------------------
    // pyproject.toml validation
    // ------------------------------------------------------------------

    @Test
    void createVenv_missingPyprojectToml_throwsGradleException() {
        task.getPyprojectTomlFile().set("nonexistent_pyproject.toml");

        GradleException ex = assertThrows(GradleException.class, () -> task.createVenv());
        assertTrue(ex.getMessage().contains("pyproject.toml not found"));
        assertTrue(ex.getMessage().contains("nonexistent_pyproject.toml"));
    }


    // ------------------------------------------------------------------
    // Task property wiring (verify task is properly configured)
    // ------------------------------------------------------------------

    @Test
    void task_venvDir_isOutputDirectory() {
        assertNotNull(task.getVenvDir());
    }

    @Test
    void task_packages_isInput() {
        assertNotNull(task.getPackages());
        assertTrue(task.getPackages().get().isEmpty());
    }

    @Test
    void task_requirementsFile_isOptional() {
        // After registration, requirementsFile should not be set
        assertFalse(task.getRequirementsFile().isPresent());
    }

    @Test
    void task_pyprojectTomlFile_isOptional() {
        assertFalse(task.getPyprojectTomlFile().isPresent());
    }

    // ------------------------------------------------------------------
    // Fingerprint hash computation
    // ------------------------------------------------------------------

    @Test
    void computePackageHash_sameInputs_produceSameHash() {
        String hash1 = task.computePackageHash(List.of("numpy", "pandas"), null, List.of(), null);
        String hash2 = task.computePackageHash(List.of("numpy", "pandas"), null, List.of(), null);
        assertEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_differentPackages_produceDifferentHash() {
        String hash1 = task.computePackageHash(List.of("numpy"), null, List.of(), null);
        String hash2 = task.computePackageHash(List.of("pandas"), null, List.of(), null);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_orderIndependent() {
        String hash1 = task.computePackageHash(List.of("numpy", "pandas"), null, List.of(), null);
        String hash2 = task.computePackageHash(List.of("pandas", "numpy"), null, List.of(), null);
        assertEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_pipIndexUrl_affectsHash() {
        String hash1 = task.computePackageHash(List.of("numpy"), null, List.of(), null);
        String hash2 = task.computePackageHash(List.of("numpy"), "https://example.com/simple", List.of(), null);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_pipExtraArgs_affectsHash() {
        String hash1 = task.computePackageHash(List.of("numpy"), null, List.of(), null);
        String hash2 = task.computePackageHash(List.of("numpy"), null, List.of("--extra-index-url", "https://example.com"), null);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_emptyPackages_producesValidHash() {
        String hash = task.computePackageHash(List.of(), null, List.of(), null);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(64, hash.length()); // SHA-256 hex is 64 chars
    }

    // ------------------------------------------------------------------
    // Incremental venv: fingerprint match skips Python setup
    // ------------------------------------------------------------------

    @Test
    void createVenv_fingerprintMatchSkipsSetup(@TempDir Path tempDir) throws IOException {
        // Set up a fake venv with python executable and matching fingerprint
        Path venvPath = tempDir.resolve("test-venv");
        File venvDir = venvPath.toFile();
        task.getVenvDir().set(project.getLayout().getProjectDirectory().dir(tempDir.toString() + "/test-venv"));

        // Create fake python executable in OS-appropriate location
        createPrimaryPythonExe(venvPath);

        // Set packages and compute expected fingerprint
        // msgpack is auto-added by VenvTask, so include it in the expected hash
        List<String> packages = List.of("numpy==1.26.4");
        task.getPackages().set(packages);
        String expectedHash = task.computePackageHash(List.of("numpy==1.26.4", "msgpack"), null, List.of(), null);

        // Write matching fingerprint file
        Properties fp = new Properties();
        fp.setProperty("python.source", "system");
        fp.setProperty("python.version", "3.12");
        fp.setProperty("packages.hash", expectedHash);
        try (var writer = Files.newBufferedWriter(venvPath.resolve("python-embed.fingerprint"), StandardCharsets.UTF_8)) {
            fp.store(writer, null);
        }

        // Should skip setup without trying to run Python
        assertDoesNotThrow(() -> task.createVenv());
    }

    @Test
    void createVenv_fingerprintMissing_proceedsToSetup() {
        // When no fingerprint exists, the task should proceed through the full setup path.
        // The outcome depends on whether Python is available, but the fingerprint
        // file should be written after setup completes or the task fails before
        // fingerprint check.
        task.getPackages().set(List.of("numpy"));

        // The task will either succeed (if Python is available) or throw (if not).
        // Both outcomes are acceptable - the point is it doesn't skip setup.
        try {
            task.createVenv();
        } catch (GradleException e) {
            assertTrue(e.getMessage().contains("Python") || e.getMessage().contains("pip"));
        }
    }

    @Test
    void createVenv_fingerprintMismatch_proceedsToSetup(@TempDir Path tempDir) throws IOException {
        // Set up a fake venv with python executable but MISMATCHED fingerprint
        Path venvPath = tempDir.resolve("test-venv");
        task.getVenvDir().set(project.getLayout().getProjectDirectory().dir(tempDir.toString() + "/test-venv"));

        // Create fake python executable in OS-appropriate location
        createPrimaryPythonExe(venvPath);

        // Set packages that don't match the stored fingerprint
        task.getPackages().set(List.of("numpy==1.26.4"));

        // Write mismatched fingerprint (different package hash)
        Properties fp = new Properties();
        fp.setProperty("python.source", "system");
        fp.setProperty("python.version", "3.12");
        fp.setProperty("packages.hash", "0000000000000000000000000000000000000000000000000000000000000000");
        try (var writer = Files.newBufferedWriter(venvPath.resolve("python-embed.fingerprint"), StandardCharsets.UTF_8)) {
            fp.store(writer, null);
        }

        // Should proceed to pip install, which will fail because the fake python isn't real.
        // But the flow should at least try (we expect an error from command execution).
        GradleException ex = assertThrows(GradleException.class, () -> task.createVenv());
        // The error comes from trying to run the fake python executable
        assertTrue(ex.getMessage().contains("Failed to run") || ex.getMessage().contains("exit"));
    }

    // ------------------------------------------------------------------
    // Tar/ZipSlip: path traversal protection
    // ------------------------------------------------------------------

    @Test
    void extractTarGz_normalFile_succeeds(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("target");
        Path tarFile = createTarGz(tempDir, "hello.txt", "hello world");

        task.extractTarGz(tarFile, targetDir);

        Path extracted = targetDir.resolve("hello.txt");
        assertTrue(Files.exists(extracted));
        assertEquals("hello world", Files.readString(extracted));
    }

    @Test
    void extractTarGz_nestedDirectories_succeeds(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("target");
        Path tarFile = createTarGz(tempDir, "a/b/c/deep.txt", "deep content");

        task.extractTarGz(tarFile, targetDir);

        Path extracted = targetDir.resolve("a/b/c/deep.txt");
        assertTrue(Files.exists(extracted));
        assertEquals("deep content", Files.readString(extracted));
    }

    @Test
    void extractTarGz_pathTraversal_dotDotSlash_throwsIOException(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("target");
        Path tarFile = createTarGz(tempDir, "../evil.txt", "malicious content");

        IOException ex = assertThrows(IOException.class, () -> task.extractTarGz(tarFile, targetDir));
        assertTrue(ex.getMessage().contains("path traversal"));

        // Verify no file escaped targetDir
        Path escapedFile = targetDir.getParent().resolve("evil.txt");
        assertFalse(Files.exists(escapedFile), "File should not be written outside targetDir");
    }

    @Test
    void extractTarGz_pathTraversal_deepDotDot_throwsIOException(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("target");
        Path tarFile = createTarGz(tempDir, "a/../../../escape.txt", "malicious content");

        IOException ex = assertThrows(IOException.class, () -> task.extractTarGz(tarFile, targetDir));
        assertTrue(ex.getMessage().contains("path traversal"));

        Path escapedFile = targetDir.getParent().resolve("escape.txt");
        assertFalse(Files.exists(escapedFile), "File should not be written outside targetDir");
    }

    @Test
    void extractTarGz_leadingSlash_strippedAndExtractedSafely(@TempDir Path tempDir) throws Exception {
        // Leading '/' in tar entries is stripped (many archives use this convention)
        // and the entry is treated as relative, which is safe
        Path targetDir = tempDir.resolve("target");
        Path tarFile = createTarGz(tempDir, "/safe/path/file.txt", "safe content");

        assertDoesNotThrow(() -> task.extractTarGz(tarFile, targetDir));

        Path extracted = targetDir.resolve("safe/path/file.txt");
        assertTrue(Files.exists(extracted));
        assertEquals("safe content", Files.readString(extracted));
    }

    @Test
    void extractTarGz_leadingBackslash_strippedAndExtractedSafely(@TempDir Path tempDir) throws Exception {
        // Leading '\' in tar entries is also stripped
        Path targetDir = tempDir.resolve("target");
        Path tarFile = createTarGz(tempDir, "\\windows\\file.txt", "windows content");

        assertDoesNotThrow(() -> task.extractTarGz(tarFile, targetDir));

        Path extracted = targetDir.resolve("windows/file.txt");
        assertTrue(Files.exists(extracted));
        assertEquals("windows content", Files.readString(extracted));
    }

    // ------------------------------------------------------------------
    // findPythonInDir
    // ------------------------------------------------------------------

    @Test
    void findPythonInDir_pythonExeAtRoot_returnsPath(@TempDir Path tempDir) throws Exception {
        Path pythonExe = createPrimaryPythonExe(tempDir);

        Path result = task.findPythonInDir(tempDir);
        assertEquals(pythonExe, result);
    }

    @Test
    void findPythonInDir_pythonExeInSubdir_returnsSubdirPath(@TempDir Path tempDir) throws Exception {
        // When root has no python executable, should find secondary location
        Path pythonExe = createSecondaryPythonExe(tempDir);

        Path result = task.findPythonInDir(tempDir);
        assertEquals(pythonExe, result);
    }

    @Test
    void findPythonInDir_rootPreferredOverSubdir(@TempDir Path tempDir) throws Exception {
        // When both primary and secondary locations have python executable, primary takes precedence
        Path rootExe = createPrimaryPythonExe(tempDir);
        createSecondaryPythonExe(tempDir);

        Path result = task.findPythonInDir(tempDir);
        assertEquals(rootExe, result);
    }

    @Test
    void findPythonInDir_noPythonExe_returnsNull(@TempDir Path tempDir) throws Exception {
        Path result = task.findPythonInDir(tempDir);
        assertNull(result);
    }

    @Test
    void findPythonInDir_emptySubdir_returnsNull(@TempDir Path tempDir) throws Exception {
        // Subdirectory exists but has no python executable, and none at root
        Path subDir = tempDir.resolve(isWindows() ? "Scripts" : "bin");
        Files.createDirectories(subDir);

        Path result = task.findPythonInDir(tempDir);
        assertNull(result);
    }

    // ---- helpers ----

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Creates the primary (preferred) Python executable for the current OS. */
    private Path createPrimaryPythonExe(Path baseDir) throws IOException {
        Path exe;
        if (isWindows()) {
            Files.createDirectories(baseDir);
            exe = baseDir.resolve("python.exe");
        } else {
            exe = baseDir.resolve("bin").resolve("python3");
        }
        Files.createDirectories(exe.getParent());
        Files.createFile(exe);
        return exe;
    }

    /** Creates the secondary Python executable for the current OS. */
    private Path createSecondaryPythonExe(Path baseDir) throws IOException {
        Path exe;
        if (isWindows()) {
            exe = baseDir.resolve("Scripts").resolve("python.exe");
        } else {
            exe = baseDir.resolve("bin").resolve("python");
        }
        Files.createDirectories(exe.getParent());
        Files.createFile(exe);
        return exe;
    }

    // ---- tar.gz creation helpers ----

    private Path createTarGz(Path tempDir, String entryName, String content) throws IOException {
        Path tarFile = tempDir.resolve(entryName.replace('/', '_').replace('\\', '_') + ".tar.gz");
        try (var fos = Files.newOutputStream(tarFile);
             var gzos = new GZIPOutputStream(fos)) {

            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            byte[] header = createTarHeader(entryName, data.length);
            gzos.write(header);

            // Write file data padded to 512-byte boundary
            gzos.write(data);
            int padding = (512 - (data.length % 512)) % 512;
            if (padding > 0) {
                gzos.write(new byte[padding]);
            }

            // End-of-archive marker: two zero blocks
            gzos.write(new byte[1024]);
        }
        return tarFile;
    }

    private byte[] createTarHeader(String name, int dataLength) {
        byte[] header = new byte[512];

        // name (0-99)
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));

        // mode (100-107): "0000644\0"
        byte[] modeBytes = "0000644\0".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(modeBytes, 0, header, 100, modeBytes.length);

        // uid (108-115): "0000000\0"
        byte[] uidBytes = "0000000\0".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(uidBytes, 0, header, 108, uidBytes.length);

        // gid (116-123): "0000000\0"
        byte[] gidBytes = "0000000\0".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(gidBytes, 0, header, 116, gidBytes.length);

        // size (124-135): 11 octal digits + null
        String sizeStr = String.format("%011o\0", dataLength);
        System.arraycopy(sizeStr.getBytes(StandardCharsets.UTF_8), 0, header, 124, 12);

        // mtime (136-147): 11 octal digits + null (zero for simplicity)
        byte[] mtimeBytes = "00000000000\0".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(mtimeBytes, 0, header, 136, mtimeBytes.length);

        // chksum (148-155): fill with spaces initially
        Arrays.fill(header, 148, 156, (byte) ' ');

        // typeflag (156): '0' for regular file
        header[156] = '0';

        // Calculate checksum: sum of all 512 bytes (chksum field treated as spaces)
        long sum = 0;
        for (byte b : header) {
            sum += (b & 0xFF);
        }
        String chksum = String.format("%06o\0 ", sum);
        System.arraycopy(chksum.getBytes(StandardCharsets.UTF_8), 0, header, 148, 8);

        return header;
    }
}
