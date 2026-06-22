package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for PythonEmbed initialization paths:
 * {@code resolveFromProperties()} all branches and
 * {@code loadProperties()} edge cases.
 */
class PythonEmbedInitializationTest {

    private PythonEmbed embed;
    private Method resolveFromProperties;
    private Method loadProperties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        embed = new PythonEmbed(PythonEmbed.Options.defaults());

        resolveFromProperties = PythonEmbed.class.getDeclaredMethod(
                "resolveFromProperties", Properties.class);
        resolveFromProperties.setAccessible(true);

        loadProperties = PythonEmbed.class.getDeclaredMethod("loadProperties");
        loadProperties.setAccessible(true);
    }

    @AfterEach
    void tearDown() {
        if (embed != null) {
            embed.close();
        }
    }

    // ------------------------------------------------------------------
    // resolveFromProperties - branches
    // ------------------------------------------------------------------

    @Test
    void resolveFromProperties_nullProps_returnsNull() throws Exception {
        Path result = (Path) resolveFromProperties.invoke(embed, (Properties) null);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedDefaultTrue_pathIsDirectory_returnsPath() throws Exception {
        Path venvDir = Files.createDirectory(tempDir.resolve("venv-embedded"));
        Properties props = new Properties();
        props.setProperty("venv.path", venvDir.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertEquals(venvDir, result);
    }

    @Test
    void resolveFromProperties_embeddedDefaultTrue_pathNull_returnsNull() throws Exception {
        Properties props = new Properties();

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedDefaultTrue_pathNotDirectory_returnsNull() throws Exception {
        Path file = Files.createFile(tempDir.resolve("not-a-dir.txt"));
        Properties props = new Properties();
        props.setProperty("venv.path", file.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedExplicitTrue_pathIsDirectory_returnsPath() throws Exception {
        Path venvDir = Files.createDirectory(tempDir.resolve("venv-explicit-true"));
        Properties props = new Properties();
        props.setProperty("venv.embedded", "true");
        props.setProperty("venv.path", venvDir.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertEquals(venvDir, result);
    }

    @Test
    void resolveFromProperties_embeddedExplicitTrue_pathNull_returnsNull() throws Exception {
        Properties props = new Properties();
        props.setProperty("venv.embedded", "true");

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedExplicitTrue_pathNotDirectory_returnsNull() throws Exception {
        Path file = Files.createFile(tempDir.resolve("not-dir-explicit.txt"));
        Properties props = new Properties();
        props.setProperty("venv.embedded", "true");
        props.setProperty("venv.path", file.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedFalse_pathIsDirectory_returnsPath() throws Exception {
        Path venvDir = Files.createDirectory(tempDir.resolve("venv-external"));
        Properties props = new Properties();
        props.setProperty("venv.embedded", "false");
        props.setProperty("venv.path", venvDir.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertEquals(venvDir, result);
    }

    @Test
    void resolveFromProperties_embeddedFalse_pathNull_returnsNull() throws Exception {
        Properties props = new Properties();
        props.setProperty("venv.embedded", "false");

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedFalse_pathEmpty_returnsNull() throws Exception {
        Properties props = new Properties();
        props.setProperty("venv.embedded", "false");
        props.setProperty("venv.path", "");

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedFalse_pathNotDirectory_returnsNull() throws Exception {
        Path file = Files.createFile(tempDir.resolve("not-dir-external.txt"));
        Properties props = new Properties();
        props.setProperty("venv.embedded", "false");
        props.setProperty("venv.path", file.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    @Test
    void resolveFromProperties_embeddedNonBoolean_treatedAsFalse_pathIsDirectory_returnsPath()
            throws Exception {
        Path venvDir = Files.createDirectory(tempDir.resolve("venv-invalid-bool"));
        Properties props = new Properties();
        props.setProperty("venv.embedded", "yes");
        props.setProperty("venv.path", venvDir.toString());

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertEquals(venvDir, result);
    }

    @Test
    void resolveFromProperties_embeddedNonBoolean_treatedAsFalse_pathNull_returnsNull()
            throws Exception {
        Properties props = new Properties();
        props.setProperty("venv.embedded", "invalid");

        Path result = (Path) resolveFromProperties.invoke(embed, props);
        assertNull(result);
    }

    // ------------------------------------------------------------------
    // loadProperties - edge cases
    // ------------------------------------------------------------------

    @Test
    void loadProperties_resourceOnClasspath_returnsProperties() throws Exception {
        Properties result = (Properties) loadProperties.invoke(embed);
        assertNotNull(result,
                "Expected non-null Properties when META-INF/python-embed.properties is on the classpath");
    }

    @Test
    void loadProperties_containsExpectedKeys() throws Exception {
        Properties result = (Properties) loadProperties.invoke(embed);
        assertNotNull(result);
        assertNotNull(result.getProperty("venv.path"),
                "venv.path should be present");
        assertNotNull(result.getProperty("venv.embedded"),
                "venv.embedded should be present");
    }

    @Test
    void loadProperties_isIdempotent() throws Exception {
        Properties first = (Properties) loadProperties.invoke(embed);
        Properties second = (Properties) loadProperties.invoke(embed);
        assertNotNull(first);
        assertEquals(first.getProperty("venv.path"), second.getProperty("venv.path"));
        assertEquals(first.getProperty("venv.embedded"), second.getProperty("venv.embedded"));
    }
}
