package io.github.howtis.pythonembed.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Extracts .tar.gz archives without external dependencies.
 */
public final class TarGzExtractor {

    private TarGzExtractor() {
    }

    /**
     * Extracts a tar.gz file to the given target directory.
     *
     * @param tarGzFile path to the .tar.gz file
     * @param targetDir destination directory
     * @throws IOException if extraction fails
     */
    public static void extract(Path tarGzFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        // Links whose target has not been extracted yet, or which could not be
        // created as symlinks (Windows needs a privilege for that).
        List<String[]> deferredLinks = new ArrayList<>();

        try (InputStream fis = Files.newInputStream(tarGzFile);
             GZIPInputStream gzis = new GZIPInputStream(fis)) {

            byte[] buffer = new byte[8192];
            byte[] header = new byte[512];

            while (true) {
                int headerBytes = readFully(gzis, header);
                if (headerBytes < 512) break;
                if (isAllZeros(header)) break;

                String name = new String(header, 0, 100, StandardCharsets.UTF_8)
                        .replaceAll("\0.*", "").trim();
                if (name.isEmpty()) break;

                long size = parseOctal(new String(header, 124, 12, StandardCharsets.UTF_8));
                int typeFlag = header[156] & 0xFF;

                String safeName = name.replace('\\', '/');
                while (safeName.startsWith("/")) {
                    safeName = safeName.substring(1);
                }
                Path entryTarget = targetDir.resolve(safeName).normalize();
                if (!entryTarget.startsWith(targetDir.normalize())) {
                    throw new IOException(
                            "Tar entry path traversal detected: '" + name
                            + "' would extract to '" + entryTarget + "' outside target '" + targetDir + "'");
                }

                if (typeFlag == '5') {
                    Files.createDirectories(entryTarget);
                } else if (typeFlag == '2') {
                    // Symbolic link. python-build-standalone relies on these: on
                    // Linux the interpreter is reached through `bin/python3 ->
                    // python3.12`, so ignoring the entry leaves no runnable python.
                    String linkName = new String(header, 157, 100, StandardCharsets.UTF_8)
                            .replaceAll("\0.*", "").trim();
                    Path parent = entryTarget.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    createLink(entryTarget, linkName, targetDir, deferredLinks);
                } else if (typeFlag == '0' || typeFlag == ' ' || typeFlag == '7') {
                    Path parent = entryTarget.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (var fos = Files.newOutputStream(entryTarget)) {
                        long remaining = size;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(remaining, buffer.length);
                            int n = gzis.read(buffer, 0, toRead);
                            if (n < 0) throw new IOException("Unexpected EOF in tar entry");
                            fos.write(buffer, 0, n);
                            remaining -= n;
                        }
                    }
                }
                long padding = (512 - (size % 512)) % 512;
                if (padding > 0) {
                    gzis.skip(padding);
                }
            }
        }

        for (String[] link : deferredLinks) {
            resolveLink(targetDir.resolve(link[0]), link[1], targetDir);
        }
    }

    /**
     * Creates a symbolic link, or records it for later resolution when the
     * platform refuses or the target is not extracted yet.
     */
    private static void createLink(Path linkPath, String linkTarget, Path targetDir,
                                   List<String[]> deferredLinks) throws IOException {
        Path resolved = linkPath.getParent() == null
                ? linkPath.resolve(linkTarget).normalize()
                : linkPath.getParent().resolve(linkTarget).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new IOException("Tar link traversal detected: '" + linkPath
                    + "' -> '" + linkTarget + "' outside target '" + targetDir + "'");
        }
        try {
            Files.createSymbolicLink(linkPath, Path.of(linkTarget));
        } catch (IOException | UnsupportedOperationException e) {
            deferredLinks.add(new String[]{targetDir.relativize(linkPath).toString(), linkTarget});
        }
    }

    /**
     * Fallback for platforms that cannot create symlinks: copy the target file
     * instead, so the interpreter is still reachable under the expected name.
     */
    private static void resolveLink(Path linkPath, String linkTarget, Path targetDir)
            throws IOException {
        Path resolved = linkPath.getParent() == null
                ? linkPath.resolve(linkTarget).normalize()
                : linkPath.getParent().resolve(linkTarget).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new IOException("Tar link traversal detected: '" + linkPath
                    + "' -> '" + linkTarget + "'");
        }
        if (!Files.exists(resolved)) {
            throw new IOException("Tar link target missing: '" + linkPath
                    + "' -> '" + resolved + "'");
        }
        if (Files.exists(linkPath)) {
            return;
        }
        Files.copy(resolved, linkPath, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static int readFully(InputStream is, byte[] buf) throws IOException {
        int offset = 0;
        int remaining = buf.length;
        while (remaining > 0) {
            int n = is.read(buf, offset, remaining);
            if (n < 0) break;
            offset += n;
            remaining -= n;
        }
        return offset;
    }

    private static boolean isAllZeros(byte[] buf) {
        for (byte b : buf) {
            if (b != 0) return false;
        }
        return true;
    }

    private static long parseOctal(String s) {
        long result = 0;
        for (char c : s.trim().toCharArray()) {
            if (c >= '0' && c <= '7') {
                result = result * 8 + (c - '0');
            } else {
                break;
            }
        }
        return result;
    }
}
