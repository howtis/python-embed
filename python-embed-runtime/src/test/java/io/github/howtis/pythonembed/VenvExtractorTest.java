package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VenvExtractorTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void extract_filesystemResource_copiesFiles() throws Exception {
        VenvExtractor extractor = new VenvExtractor();
        Path extracted = extractor.extract("test-extract");

        assertTrue(Files.exists(extracted));
        assertTrue(Files.isDirectory(extracted));

        Path helloFile = extracted.resolve("hello.txt");
        assertTrue(Files.exists(helloFile));
        assertEquals("Hello from test resources!", Files.readString(helloFile).trim());

        // Directory structure preserved
        Path nestedDir = extracted.resolve("subdir");
        assertTrue(Files.isDirectory(nestedDir));
        Path nestedFile = nestedDir.resolve("nested.txt");
        assertTrue(Files.exists(nestedFile));
        assertEquals("nested content", Files.readString(nestedFile).trim());

        extractor.close();
        assertFalse(Files.exists(extracted), "temp dir should be cleaned up on close");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void close_cleansUpTempDir() throws Exception {
        VenvExtractor extractor = new VenvExtractor();
        Path extracted = extractor.extract("test-extract");
        assertTrue(Files.exists(extracted));

        extractor.close();
        assertFalse(Files.exists(extracted));
    }

    @Test
    void extract_afterClose_throws() throws Exception {
        VenvExtractor extractor = new VenvExtractor();
        extractor.extract("test-extract");
        extractor.close();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> extractor.extract("test-extract"));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void extract_resourceNotFound_throws() {
        VenvExtractor extractor = new VenvExtractor();
        IOException ex = assertThrows(IOException.class,
                () -> extractor.extract("nonexistent-resource-path"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void extract_returnsDifferentPathEachTime() throws Exception {
        VenvExtractor e1 = new VenvExtractor();
        VenvExtractor e2 = new VenvExtractor();

        Path p1 = e1.extract("test-extract");
        Path p2 = e2.extract("test-extract");

        assertNotEquals(p1, p2, "each extraction should get a unique temp directory");
        assertTrue(Files.exists(p1));
        assertTrue(Files.exists(p2));

        e1.close();
        e2.close();
    }

    /**
     * Reproduces the packaged-application path: the venv lives inside a JAR, not on
     * a filesystem. On Windows the file URI inside a {@code jar:} URL is
     * {@code file:/C:/...}, which the zip filesystem provider rejects unless it is
     * converted through {@link Path#of(java.net.URI)} first.
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void extract_fromJar_copiesFiles(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("app.jar");
        Path staged = tempDir.resolve("staging");
        Path nested = staged.resolve("python-venv").resolve("bin");
        Files.createDirectories(nested);
        Files.writeString(staged.resolve("python-venv").resolve("python.exe"), "fake-exe");
        Files.writeString(nested.resolve("python3"), "fake-unix-exe");

        try (var out = Files.newOutputStream(jar);
             var zos = new java.util.zip.ZipOutputStream(out)) {
            try (var walk = Files.walk(staged)) {
                for (Path entryPath : walk.sorted().toList()) {
                    String entry = staged.relativize(entryPath).toString().replace('\\', '/');
                    if (entry.isEmpty()) {
                        continue;
                    }
                    // JARs built by Gradle carry directory entries, and resolving a
                    // directory resource needs one.
                    if (Files.isDirectory(entryPath)) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(entry + "/"));
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new java.util.zip.ZipEntry(entry));
                        zos.write(Files.readAllBytes(entryPath));
                        zos.closeEntry();
                    }
                }
            }
        }

        try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{jar.toUri().toURL()}, null)) {
            VenvExtractor extractor = new VenvExtractor();
            Path extracted = extractor.extract("python-venv", loader);

            assertTrue(Files.exists(extracted.resolve("python.exe")));
            assertEquals("fake-exe", Files.readString(extracted.resolve("python.exe")));
            assertTrue(Files.exists(extracted.resolve("bin").resolve("python3")));

            extractor.close();
            assertFalse(Files.exists(extracted));
        }
    }
}
