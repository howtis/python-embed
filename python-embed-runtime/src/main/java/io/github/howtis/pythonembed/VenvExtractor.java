package io.github.howtis.pythonembed;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts a directory from classpath resources (JAR or filesystem)
 * into a temporary directory for use at runtime.
 *
 * <p>Handles both JAR-internal paths (nested filesystem via {@link FileSystem})
 * and plain filesystem paths (IDE/dev mode).
 */
class VenvExtractor implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(VenvExtractor.class.getName());

    private Path tempDir;
    private boolean closed;

    /**
     * Extracts the given classpath resource directory into a temporary location.
     *
     * @param resourcePath the resource directory path relative to classpath root
     *                     (e.g. "python-venv")
     * @return the path to the extracted directory on the filesystem
     */
    Path extract(String resourcePath) throws IOException {
        if (closed) {
            throw new IllegalStateException("VenvExtractor is closed");
        }

        URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Resource not found on classpath: " + resourcePath);
        }

        tempDir = Files.createTempDirectory("python-embed-venv-");
        logger.info(() -> "Extracting venv to " + tempDir);

        URI uri = URI.create(resourceUrl.toString());
        String scheme = uri.getScheme();

        if ("jar".equals(scheme)) {
            extractFromJar(uri);
        } else {
            // Filesystem (IDE/dev mode)
            Path sourceDir = Path.of(uri);
            copyDirectory(sourceDir, tempDir);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupTempDir, "venv-cleanup"));

        logger.info("Venv extraction complete: " + tempDir);
        return tempDir;
    }

    @Override
    public void close() {
        closed = true;
        cleanupTempDir();
    }

    // ---- internal ----

    private void extractFromJar(URI jarUri) throws IOException {
        // The URI looks like: jar:file:/path/to.jar!/resourcePath
        String[] parts = jarUri.toString().split("!");
        if (parts.length < 2) {
            throw new IOException("Invalid jar URI: " + jarUri);
        }

        URI jarFileUri = URI.create(parts[0].replace("jar:", ""));
        String entryPrefix = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];

        try (FileSystem jarFs = FileSystems.newFileSystem(jarFileUri, Collections.emptyMap())) {
            Path jarRoot = jarFs.getPath(entryPrefix);
            if (!Files.exists(jarRoot)) {
                throw new IOException("Path not found in JAR: " + entryPrefix);
            }
            copyDirectory(jarRoot, tempDir);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path relativePath = source.relativize(dir);
                Path targetDir = target.resolve(relativePath.toString());
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path relativePath = source.relativize(file);
                Path targetFile = target.resolve(relativePath.toString());
                Files.copy(file, targetFile);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void cleanupTempDir() {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.log(Level.FINE, "Failed to delete: " + path, e);
                            }
                        });
                logger.fine("Cleaned up temp dir: " + tempDir);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to clean up temp dir: " + tempDir, e);
            }
        }
    }
}
