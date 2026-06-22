package io.github.howtis.pythonembed.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementsParserTest {

    @Test
    void parse_basicPackages_returnsAll(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "numpy==1.26.4\npandas>=2.0\nrequests\n");

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("numpy==1.26.4", "pandas>=2.0", "requests"), result);
    }

    @Test
    void parse_emptyLines_skipped(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "\n\nnumpy\n\n\npandas\n\n");

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("numpy", "pandas"), result);
    }

    @Test
    void parse_commentLines_skipped(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "# This is a comment\nnumpy\n# Another comment\npandas\n");

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("numpy", "pandas"), result);
    }

    @Test
    void parse_mixedContent_skipsCommentsAndEmpty(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, """
                numpy==1.26.4
                # scientific computing

                pandas>=2.0

                torch==2.4.0
                # deep learning
                """);

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("numpy==1.26.4", "pandas>=2.0", "torch==2.4.0"), result);
    }

    @Test
    void parse_emptyFile_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "");

        List<String> result = RequirementsParser.parse(reqFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_onlyComments_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "# just a comment\n# another one\n");

        List<String> result = RequirementsParser.parse(reqFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_pipOptions_skippedWithWarning(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "numpy\n-r extra.txt\n-c constraints.txt\n--index-url https://example.com\npandas\n");

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("numpy", "pandas"), result);
    }

    @Test
    void parse_packageWithExtras_preserved(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        Files.writeString(reqFile, "tensorflow[and-cuda]>=2.16.0\n");

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("tensorflow[and-cuda]>=2.16.0"), result);
    }

    @Test
    void parse_lastLineNoNewline_handled(@TempDir Path tempDir) throws IOException {
        Path reqFile = tempDir.resolve("requirements.txt");
        // Write without trailing newline
        Files.write(reqFile, List.of("numpy==1.26.4", "pandas"));

        List<String> result = RequirementsParser.parse(reqFile);

        assertEquals(List.of("numpy==1.26.4", "pandas"), result);
    }

    @Test
    void parse_fileNotFound_throwsIOException() {
        Path nonExistent = Path.of("nonexistent_requirements.txt");
        assertThrows(IOException.class, () -> RequirementsParser.parse(nonExistent));
    }
}
