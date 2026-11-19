package io.github.howtis.pythonembed.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonDownloaderTest {

    private static final String WIN_TRIPLE = "x86_64-pc-windows-msvc";

    // ---------------------------------------------------------------- asset matching

    @Test
    void findMatchingAsset_picksInstallOnlyForExactVersionAndTriple() throws IOException {
        List<String> assets = List.of(
                "cpython-3.12.16+20261110-x86_64-pc-windows-msvc-install_only_stripped.tar.gz",
                "cpython-3.12.16+20261110-aarch64-pc-windows-msvc-install_only.tar.gz",
                "cpython-3.13.18+20261110-x86_64-pc-windows-msvc-install_only.tar.gz",
                "cpython-3.12.16+20261110-x86_64-pc-windows-msvc-install_only.tar.gz",
                "SHA256SUMS");

        String asset = PythonDownloader.findMatchingAsset(assets, "3.12", WIN_TRIPLE);

        assertEquals("cpython-3.12.16+20261110-x86_64-pc-windows-msvc-install_only.tar.gz", asset);
    }

    @Test
    void findMatchingAsset_doesNotMatchUnrelatedVersionPrefix() {
        List<String> assets = List.of("cpython-3.1.5+20261110-x86_64-pc-windows-msvc-install_only.tar.gz");

        IOException error = assertThrows(IOException.class,
                () -> PythonDownloader.findMatchingAsset(assets, "3.12", WIN_TRIPLE));

        assertTrue(error.getMessage().contains("No matching python-build-standalone asset"),
                error.getMessage());
    }

    @Test
    void findMatchingAsset_throwsWhenTripleAbsent() {
        List<String> assets = List.of("cpython-3.12.16+20261110-x86_64-unknown-linux-gnu-install_only.tar.gz");

        assertThrows(IOException.class,
                () -> PythonDownloader.findMatchingAsset(assets, "3.12", WIN_TRIPLE));
    }

    // ---------------------------------------------------------------- SHA256SUMS parsing

    @Test
    void parseSha256Sums_returnsDigestForAsset() {
        String sums = String.join("\n",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  cpython-3.11.18+20261110-x86_64-pc-windows-msvc-install_only.tar.gz",
                "e23fe5b16926de1d81661b1a97352e89fb167549c55a6825796ccd0f32ec7f1b  cpython-3.12.16+20261110-x86_64-pc-windows-msvc-install_only.tar.gz");

        String digest = PythonDownloader.parseSha256Sums(sums,
                "cpython-3.12.16+20261110-x86_64-pc-windows-msvc-install_only.tar.gz");

        assertEquals("e23fe5b16926de1d81661b1a97352e89fb167549c55a6825796ccd0f32ec7f1b", digest);
    }

    @Test
    void parseSha256Sums_handlesBinaryMarkerAndCrLf() {
        String sums = "ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789 *python.tar.gz\r\n";

        String digest = PythonDownloader.parseSha256Sums(sums, "python.tar.gz");

        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", digest);
    }

    @Test
    void parseSha256Sums_returnsNullWhenAssetMissing() {
        String sums = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  other.tar.gz\n";

        assertNull(PythonDownloader.parseSha256Sums(sums, "python.tar.gz"));
    }

    @Test
    void parseSha256Sums_ignoresBlankAndMalformedLines() {
        String sums = "\n\n   \nnotahashline\n  cpython.tar.gz  \n";

        assertNull(PythonDownloader.parseSha256Sums(sums, "cpython.tar.gz"));
    }

    // ---------------------------------------------------------------- metadata cache

    @Test
    void releaseMetadataCache_roundTrips(@TempDir Path cacheDir) throws IOException {
        Path cacheFile = cacheDir.resolve("release-metadata.properties");
        PythonDownloader.ReleaseMetadata original = new PythonDownloader.ReleaseMetadata(
                "20261110",
                List.of("cpython-3.12.16+20261110-x86_64-pc-windows-msvc-install_only.tar.gz",
                        "SHA256SUMS"));

        PythonDownloader.writeReleaseMetadataCache(cacheFile, original);
        PythonDownloader.ReleaseMetadata restored =
                PythonDownloader.readReleaseMetadataCache(cacheFile);

        assertEquals(original.tag(), restored.tag());
        assertEquals(original.assets(), restored.assets());
    }

    @Test
    void releaseMetadataCache_missingFileReturnsNull(@TempDir Path cacheDir) {
        assertNull(PythonDownloader.readReleaseMetadataCache(
                cacheDir.resolve("release-metadata.properties")));
    }

    @Test
    void releaseMetadataCache_incompleteFileReturnsNull(@TempDir Path cacheDir) throws IOException {
        Path cacheFile = cacheDir.resolve("release-metadata.properties");
        Files.writeString(cacheFile, "tag=20261110\n");

        assertNull(PythonDownloader.readReleaseMetadataCache(cacheFile));
    }

    @Test
    void releaseMetadataCache_survivesCachedAssetsWithSpaces(@TempDir Path cacheDir) throws IOException {
        Path cacheFile = cacheDir.resolve("release-metadata.properties");
        PythonDownloader.writeReleaseMetadataCache(cacheFile,
                new PythonDownloader.ReleaseMetadata("20261110", List.of("asset with space.tar.gz")));

        PythonDownloader.ReleaseMetadata restored =
                PythonDownloader.readReleaseMetadataCache(cacheFile);

        assertEquals(List.of("asset with space.tar.gz"), restored.assets());
    }
}
