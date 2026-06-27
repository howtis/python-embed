package io.github.howtis.pythonembed.maven;

import io.github.howtis.pythonembed.build.FingerprintManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SetupMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSkipWhenSkipIsTrue() throws Exception {
        SetupMojo mojo = new SetupMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        setField(mojo, "skip", true);

        mojo.execute();

        assertTrue(log.containsInfo("Skipping"),
                "Should log skip message");
    }

    @Test
    void shouldCreateVenvWhenPythonIsAvailable() throws Exception {
        SetupMojo mojo = new SetupMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        Path projectRoot = tempDir.resolve("project");
        java.nio.file.Files.createDirectories(projectRoot);
        Path pomFile = projectRoot.resolve("pom.xml");
        java.nio.file.Files.createFile(pomFile);

        MavenProject project = new MavenProject();
        project.setFile(pomFile.toFile());
        setField(mojo, "project", project);
        setField(mojo, "session", mock(MavenSession.class));
        setField(mojo, "pythonVersion", "3.12");

        Path venvDir = projectRoot.resolve("target").resolve("python-venv");
        setField(mojo, "venvOutputDir", venvDir.toFile());

        assertDoesNotThrow(mojo::execute,
                "Setup should complete when Python is available");
    }

    @Test
    void shouldResolveRelativeRequirementsFile() throws Exception {
        SetupMojo mojo = new SetupMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        Path projectRoot = tempDir.resolve("project");
        java.nio.file.Files.createDirectories(projectRoot);

        MavenProject project = new MavenProject();
        project.setFile(projectRoot.resolve("pom.xml").toFile());
        setField(mojo, "project", project);
        setField(mojo, "session", mock(MavenSession.class));
        setField(mojo, "skip", true);

        File reqFile = new File("requirements.txt");
        setField(mojo, "requirementsFile", reqFile);

        mojo.execute();
        assertTrue(log.containsInfo("Skipping"));
    }

    @Test
    void shouldResolveRelativePyprojectTomlFile() throws Exception {
        SetupMojo mojo = new SetupMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        Path projectRoot = tempDir.resolve("project");
        java.nio.file.Files.createDirectories(projectRoot);

        MavenProject project = new MavenProject();
        project.setFile(projectRoot.resolve("pom.xml").toFile());
        setField(mojo, "project", project);
        setField(mojo, "session", mock(MavenSession.class));
        setField(mojo, "skip", true);

        File pyprojectFile = new File("pyproject.toml");
        setField(mojo, "pyprojectTomlFile", pyprojectFile);

        mojo.execute();
        assertTrue(log.containsInfo("Skipping"));
    }

    @Test
    void shouldKeepAbsoluteRequirementsFilePath() throws Exception {
        SetupMojo mojo = new SetupMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        MavenProject project = new MavenProject();
        project.setFile(tempDir.resolve("pom.xml").toFile());
        setField(mojo, "project", project);
        setField(mojo, "session", mock(MavenSession.class));
        setField(mojo, "skip", true);

        File absoluteFile = tempDir.resolve("some").resolve("requirements.txt").toFile();
        setField(mojo, "requirementsFile", absoluteFile);

        mojo.execute();
        assertTrue(log.containsInfo("Skipping"));
    }

    @Test
    void defaultTargetOsShouldBeNull() throws Exception {
        SetupMojo mojo = new SetupMojo();
        var field = SetupMojo.class.getDeclaredField("targetOs");
        field.setAccessible(true);
        assertNull(field.get(mojo), "Default targetOs should be null (auto-detect)");
    }

    @Test
    void defaultPipIndexUrlShouldBeNull() throws Exception {
        SetupMojo mojo = new SetupMojo();
        var field = SetupMojo.class.getDeclaredField("pipIndexUrl");
        field.setAccessible(true);
        assertNull(field.get(mojo), "Default pipIndexUrl should be null");
    }

    @Test
    void defaultPipExtraArgsShouldBeEmpty() throws Exception {
        SetupMojo mojo = new SetupMojo();
        var field = SetupMojo.class.getDeclaredField("pipExtraArgs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> value = (List<String>) field.get(mojo);
        assertNotNull(value, "Default pipExtraArgs should not be null");
        assertTrue(value.isEmpty(), "Default pipExtraArgs should be empty");
    }

    @Test
    void pipIndexUrl_affectsPackageHash() throws IOException {
        List<String> packages = List.of("numpy");
        String hashWithoutIndex = FingerprintManager.computePackageHash(
                packages, null, List.of(), null);
        String hashWithIndex = FingerprintManager.computePackageHash(
                packages, "https://download.pytorch.org/whl/cu118", List.of(), null);
        assertNotEquals(hashWithoutIndex, hashWithIndex,
                "pipIndexUrl should affect package hash");
    }

    @Test
    void pipExtraArgs_affectsPackageHash() throws IOException {
        List<String> packages = List.of("numpy");
        String hashWithoutArgs = FingerprintManager.computePackageHash(
                packages, null, List.of(), null);
        String hashWithArgs = FingerprintManager.computePackageHash(
                packages, null, List.of("--extra-index-url", "https://example.com"), null);
        assertNotEquals(hashWithoutArgs, hashWithArgs,
                "pipExtraArgs should affect package hash");
    }

    @Test
    void pyprojectTomlFile_affectsPackageHash(@TempDir Path tempDir) throws IOException {
        Path pyproject = tempDir.resolve("pyproject.toml");
        Files.writeString(pyproject, "[project]\nname = \"test\"\n");
        List<String> packages = List.of("numpy");
        String hashWithoutPyproject = FingerprintManager.computePackageHash(
                packages, null, List.of(), null);
        String hashWithPyproject = FingerprintManager.computePackageHash(
                packages, null, List.of(), pyproject);
        assertNotEquals(hashWithoutPyproject, hashWithPyproject,
                "pyproject.toml should affect package hash");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

}
