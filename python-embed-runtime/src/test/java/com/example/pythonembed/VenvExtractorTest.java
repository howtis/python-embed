package com.example.pythonembed;

import org.junit.jupiter.api.Test;
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
}
