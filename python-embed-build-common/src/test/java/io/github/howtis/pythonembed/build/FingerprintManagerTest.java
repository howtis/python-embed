package io.github.howtis.pythonembed.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FingerprintManagerTest {

    @Test
    void computePackageHash_sameInputs_produceSameHash() throws IOException {
        List<String> packages = List.of("numpy==1.26.4", "pandas>=2.0");
        String hash1 = FingerprintManager.computePackageHash(packages, null, List.of(), null);
        String hash2 = FingerprintManager.computePackageHash(packages, null, List.of(), null);
        assertEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_differentPackages_produceDifferentHash() throws IOException {
        String hash1 = FingerprintManager.computePackageHash(List.of("numpy"), null, List.of(), null);
        String hash2 = FingerprintManager.computePackageHash(List.of("pandas"), null, List.of(), null);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_orderIndependent() throws IOException {
        String hash1 = FingerprintManager.computePackageHash(
                List.of("numpy", "pandas"), null, List.of(), null);
        String hash2 = FingerprintManager.computePackageHash(
                List.of("pandas", "numpy"), null, List.of(), null);
        assertEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_pipIndexUrl_affectsHash() throws IOException {
        String hash1 = FingerprintManager.computePackageHash(
                List.of("numpy"), "https://example.com/simple", List.of(), null);
        String hash2 = FingerprintManager.computePackageHash(
                List.of("numpy"), "https://other.com/simple", List.of(), null);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_pipExtraArgs_affectsHash() throws IOException {
        String hash1 = FingerprintManager.computePackageHash(
                List.of("numpy"), null, List.of("--extra-index-url", "https://a.com"), null);
        String hash2 = FingerprintManager.computePackageHash(
                List.of("numpy"), null, List.of("--extra-index-url", "https://b.com"), null);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computePackageHash_emptyPackages_producesValidHash() throws IOException {
        String hash = FingerprintManager.computePackageHash(List.of(), null, List.of(), null);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void computePackageHash_pyprojectToml_affectsHash(@TempDir Path tempDir) throws IOException {
        Path toml1 = tempDir.resolve("pyproject1.toml");
        Path toml2 = tempDir.resolve("pyproject2.toml");
        Files.writeString(toml1, "[project]\nname = \"a\"\n");
        Files.writeString(toml2, "[project]\nname = \"b\"\n");

        String hash1 = FingerprintManager.computePackageHash(List.of(), null, List.of(), toml1);
        String hash2 = FingerprintManager.computePackageHash(List.of(), null, List.of(), toml2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void write_read_roundtrip(@TempDir Path venvDir) {
        FingerprintManager.write(venvDir, "system", "3.12", "abc123");
        FingerprintManager.Fingerprint fp = FingerprintManager.read(venvDir);
        assertNotNull(fp);
        assertEquals("system", fp.pythonSource);
        assertEquals("3.12", fp.pythonVersion);
        assertEquals("abc123", fp.packageHash);
    }

    @Test
    void read_missingFile_returnsNull(@TempDir Path venvDir) {
        FingerprintManager.Fingerprint fp = FingerprintManager.read(venvDir);
        assertNull(fp);
    }

    @Test
    void read_invalidFile_returnsNull(@TempDir Path venvDir) throws IOException {
        Files.writeString(venvDir.resolve("python-embed.fingerprint"), "not valid properties");
        FingerprintManager.Fingerprint fp = FingerprintManager.read(venvDir);
        // Missing required keys returns null
        assertNull(fp);
    }

    @Test
    void read_missingRequiredKeys_returnsNull(@TempDir Path venvDir) throws IOException {
        String content = """
                python.source=system
                """;
        Files.writeString(venvDir.resolve("python-embed.fingerprint"), content);
        FingerprintManager.Fingerprint fp = FingerprintManager.read(venvDir);
        assertNull(fp);
    }

    @Test
    void write_read_roundtrip_bundled(@TempDir Path venvDir) {
        FingerprintManager.write(venvDir, "bundled", "3.11", "def456");
        FingerprintManager.Fingerprint fp = FingerprintManager.read(venvDir);
        assertNotNull(fp);
        assertEquals("bundled", fp.pythonSource);
        assertEquals("3.11", fp.pythonVersion);
        assertEquals("def456", fp.packageHash);
    }
}
