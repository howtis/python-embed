package io.github.howtis.pythonembed.maven;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSkipWhenSkipIsTrue() throws Exception {
        PropertiesMojo mojo = new PropertiesMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        setField(mojo, "skip", true);

        mojo.execute();

        assertTrue(log.containsInfo("Skipping"),
                "Should log skip message");
    }

    @Test
    void shouldGeneratePropertiesFileWithEmbeddedTrue() throws Exception {
        Path buildDir = tempDir.resolve("target");
        Path outputDir = buildDir.resolve("classes");
        Path venvDir = buildDir.resolve("python-venv");
        Files.createDirectories(outputDir);

        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setDirectory(buildDir.toString());
        build.setOutputDirectory(outputDir.toString());
        project.setBuild(build);

        PropertiesMojo mojo = new PropertiesMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);
        setField(mojo, "project", project);
        setField(mojo, "venvOutputDir", venvDir.toFile());

        mojo.execute();

        Path propsFile = outputDir.resolve("META-INF/python-embed.properties");
        assertTrue(Files.exists(propsFile), "Properties file should exist");

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        assertEquals(venvDir.toAbsolutePath().toString(), props.getProperty("venv.path"));
        assertEquals("true", props.getProperty("venv.embedded"));
        assertTrue(log.containsInfo("Generated:"), "Should log generation message");
    }

    @Test
    void shouldGeneratePropertiesFileWithEmbeddedFalse() throws Exception {
        Path buildDir = tempDir.resolve("target");
        Path outputDir = buildDir.resolve("classes");
        Path venvDir = tempDir.resolve("external-venv");
        Files.createDirectories(outputDir);

        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setDirectory(buildDir.toString());
        build.setOutputDirectory(outputDir.toString());
        project.setBuild(build);

        PropertiesMojo mojo = new PropertiesMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);
        setField(mojo, "project", project);
        setField(mojo, "venvOutputDir", venvDir.toFile());

        mojo.execute();

        Path propsFile = outputDir.resolve("META-INF/python-embed.properties");
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        assertEquals(venvDir.toAbsolutePath().toString(), props.getProperty("venv.path"));
        assertEquals("false", props.getProperty("venv.embedded"));
    }

    @Test
    void shouldThrowExceptionWhenOutputDirCreationFails() throws Exception {
        Path buildDir = tempDir.resolve("target");
        Path outputDir = buildDir.resolve("classes");
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(buildDir);
        // Create a file where META-INF directory should be to cause failure
        Files.createDirectories(outputDir);
        Files.createFile(outputDir.resolve("META-INF"));

        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setDirectory(buildDir.toString());
        build.setOutputDirectory(outputDir.toString());
        project.setBuild(build);

        PropertiesMojo mojo = new PropertiesMojo();
        mojo.setLog(new TestLog());
        setField(mojo, "project", project);
        setField(mojo, "venvOutputDir", venvDir.toFile());

        assertThrows(MojoExecutionException.class, mojo::execute,
                "Should throw when META-INF is a file, not directory");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class TestLog implements Log {
        private final StringBuilder info = new StringBuilder();

        boolean containsInfo(String text) {
            return info.toString().contains(text);
        }

        @Override public boolean isDebugEnabled() { return false; }
        @Override public void debug(CharSequence content) {}
        @Override public void debug(CharSequence content, Throwable error) {}
        @Override public void debug(Throwable error) {}
        @Override public void info(CharSequence content) { info.append(content).append('\n'); }
        @Override public void info(CharSequence content, Throwable t) { info.append(content).append('\n'); }
        @Override public void info(Throwable t) { info.append(t.getMessage()).append('\n'); }
        @Override public void warn(CharSequence content) {}
        @Override public void warn(CharSequence content, Throwable t) {}
        @Override public void warn(Throwable t) {}
        @Override public void error(CharSequence content) {}
        @Override public void error(CharSequence content, Throwable t) {}
        @Override public void error(Throwable t) {}
        @Override public boolean isInfoEnabled() { return true; }
        @Override public boolean isWarnEnabled() { return true; }
        @Override public boolean isErrorEnabled() { return true; }
    }
}
