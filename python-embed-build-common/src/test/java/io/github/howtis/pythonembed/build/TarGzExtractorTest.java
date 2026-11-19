package io.github.howtis.pythonembed.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarGzExtractorTest {

    // ---------------------------------------------------------------- helpers

    /** Builds a minimal ustar header block. */
    private static byte[] header(String name, long size, char typeFlag, String linkName) {
        byte[] h = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, h, 0, Math.min(nameBytes.length, 100));

        writeOctal(h, 100, 8, 0644);
        writeOctal(h, 108, 8, 0);
        writeOctal(h, 116, 8, 0);
        writeOctal(h, 124, 12, size);
        writeOctal(h, 136, 12, 0);
        h[156] = (byte) typeFlag;

        if (linkName != null) {
            byte[] linkBytes = linkName.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(linkBytes, 0, h, 157, Math.min(linkBytes.length, 100));
        }

        // "ustar\0" + "00"
        System.arraycopy("ustar".getBytes(StandardCharsets.US_ASCII), 0, h, 257, 5);
        System.arraycopy("00".getBytes(StandardCharsets.US_ASCII), 0, h, 263, 2);

        // Checksum: spaces during computation, then the octal sum.
        for (int i = 148; i < 156; i++) {
            h[i] = ' ';
        }
        int sum = 0;
        for (byte b : h) {
            sum += b & 0xFF;
        }
        writeOctal(h, 148, 8, sum);
        return h;
    }

    private static void writeOctal(byte[] buf, int offset, int length, long value) {
        String s = Long.toOctalString(value);
        while (s.length() < length - 1) {
            s = "0" + s;
        }
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, Math.min(bytes.length, length - 1));
        buf[offset + length - 1] = 0;
    }

    private static void writeEntry(ByteArrayOutputStream tar, String name, char type,
                                   String linkName, byte[] content) throws IOException {
        tar.write(header(name, content == null ? 0 : content.length, type, linkName));
        if (content != null && content.length > 0) {
            tar.write(content);
            int padding = (512 - (content.length % 512)) % 512;
            tar.write(new byte[padding]);
        }
    }

    private static Path writeTarGz(Path dir, String fileName,
                                   java.util.function.Consumer<ByteArrayOutputStream> body)
            throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        body.accept(tar);
        tar.write(new byte[1024]); // end-of-archive marker

        Path file = dir.resolve(fileName);
        try (OutputStream os = Files.newOutputStream(file);
             GZIPOutputStream gz = new GZIPOutputStream(os)) {
            tar.writeTo(gz);
        }
        return file;
    }

    // ---------------------------------------------------------------- tests

    @Test
    void extract_regularFileAndDirectory(@TempDir Path tempDir) throws IOException {
        Path tgz = writeTarGz(tempDir, "plain.tar.gz", tar -> {
            try {
                writeEntry(tar, "bin/", '5', null, null);
                writeEntry(tar, "bin/tool", '0', null, "hi".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Path out = tempDir.resolve("out");
        TarGzExtractor.extract(tgz, out);

        assertEquals("hi", Files.readString(out.resolve("bin").resolve("tool")));
    }

    /**
     * python-build-standalone exposes the interpreter as {@code bin/python3 ->
     * python3.12}. Ignoring the symbolic-link entry left the archive without a
     * runnable {@code python3}, which broke every Linux build that fell back to
     * the bundled distribution.
     */
    @Test
    void extract_symbolicLink_isUsable(@TempDir Path tempDir) throws IOException {
        Path tgz = writeTarGz(tempDir, "links.tar.gz", tar -> {
            try {
                writeEntry(tar, "bin/", '5', null, null);
                writeEntry(tar, "bin/python3.12", '0', null,
                        "interpreter".getBytes(StandardCharsets.UTF_8));
                writeEntry(tar, "bin/python3", '2', "python3.12", null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Path out = tempDir.resolve("out");
        TarGzExtractor.extract(tgz, out);

        Path link = out.resolve("bin").resolve("python3");
        assertTrue(Files.exists(link), "bin/python3 must resolve to the interpreter");
        assertEquals("interpreter", Files.readString(link));
    }

    @Test
    void extract_symbolicLinkBeforeTarget_resolvedAfterExtraction(@TempDir Path tempDir)
            throws IOException {
        Path tgz = writeTarGz(tempDir, "forward.tar.gz", tar -> {
            try {
                writeEntry(tar, "bin/python3", '2', "python3.12", null);
                writeEntry(tar, "bin/python3.12", '0', null,
                        "interpreter".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Path out = tempDir.resolve("out");
        TarGzExtractor.extract(tgz, out);

        assertTrue(Files.exists(out.resolve("bin").resolve("python3")));
    }

    @Test
    void extract_symbolicLinkEscapingTarget_throws(@TempDir Path tempDir) throws IOException {
        Path tgz = writeTarGz(tempDir, "escape.tar.gz", tar -> {
            try {
                writeEntry(tar, "bin/python3", '2', "../../etc/passwd", null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        IOException error = assertThrows(IOException.class,
                () -> TarGzExtractor.extract(tgz, tempDir.resolve("out")));
        assertTrue(error.getMessage().contains("link traversal"), error.getMessage());
    }

    @Test
    void extract_pathTraversal_throws(@TempDir Path tempDir) throws IOException {
        Path tgz = writeTarGz(tempDir, "traversal.tar.gz", tar -> {
            try {
                writeEntry(tar, "../escaped.txt", '0', null, "x".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertThrows(IOException.class, () -> TarGzExtractor.extract(tgz, tempDir.resolve("out")));
    }
}
