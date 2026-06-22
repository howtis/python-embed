package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the contract between the Gradle plugin and runtime:
 * the {@code META-INF/python-embed.properties} generated at build time
 * is correctly formatted and the referenced venv path is usable.
 *
 * <p>This is the only test that explicitly verifies the cross-module
 * integration: Gradle plugin output -&gt; runtime consumption.
 */
class PythonEmbedPropertiesContractTest {

    private static final String PROPS_RESOURCE = "META-INF/python-embed.properties";
    private static final String KEY_VENV_PATH = "venv.path";
    private static final String KEY_VENV_EMBEDDED = "venv.embedded";

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void propertiesFile_existsOnClasspath() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(PROPS_RESOURCE)) {
            assertNotNull(is, "Gradle plugin should generate " + PROPS_RESOURCE);
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void propertiesFile_containsRequiredKeys() throws Exception {
        Properties props = loadProps();
        assertNotNull(props);
        assertTrue(props.containsKey(KEY_VENV_PATH), "Missing required key: " + KEY_VENV_PATH);
        assertTrue(props.containsKey(KEY_VENV_EMBEDDED), "Missing required key: " + KEY_VENV_EMBEDDED);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void propertiesFile_venvPath_exists() throws Exception {
        Properties props = loadProps();
        assertNotNull(props);
        String venvPathStr = props.getProperty(KEY_VENV_PATH);
        assertNotNull(venvPathStr, "venv.path must not be null");
        assertFalse(venvPathStr.isEmpty(), "venv.path must not be empty");

        Path venvPath = Path.of(venvPathStr);
        assertTrue(Files.exists(venvPath), "venv.path must reference an existing directory: " + venvPath);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void propertiesFile_venvEmbedded_isValidBoolean() throws Exception {
        Properties props = loadProps();
        assertNotNull(props);
        String embedded = props.getProperty(KEY_VENV_EMBEDDED);
        assertNotNull(embedded);
        assertTrue("true".equals(embedded) || "false".equals(embedded),
                "venv.embedded must be \"true\" or \"false\", got: " + embedded);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void fullPipeline_createFromProperties_works() {
        Properties props = loadProps();
        Assumptions.assumeTrue(props != null, "Properties not found");
        String venvPathStr = props.getProperty(KEY_VENV_PATH);
        Assumptions.assumeTrue(venvPathStr != null && !venvPathStr.isEmpty());
        Path venvPath = Path.of(venvPathStr);
        Assumptions.assumeTrue(Files.exists(venvPath), "Venv path not accessible: " + venvPath);

        // Full lifecycle: create -> eval -> exec -> ping -> close
        try (PythonEmbed py = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(venvPath).build())) {
            assertEquals(42, py.eval("42").asInt());
            py.exec("x = 'hello'");
            assertEquals("hello", py.eval("x").asString());
            assertTrue(py.ping());
        }
    }

    private Properties loadProps() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(PROPS_RESOURCE)) {
            if (is == null) return null;
            props.load(is);
            return props;
        } catch (Exception e) {
            return null;
        }
    }
}
