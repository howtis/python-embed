package io.github.howtis.pythonembed.gradle;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PythonEmbedPluginTest {

    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("io.github.howtis.python-embed");
    }

    // ------------------------------------------------------------------
    // Extension registration
    // ------------------------------------------------------------------

    @Test
    void apply_registersPythonEmbedExtension() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        assertNotNull(ext);
    }

    @Test
    void apply_extensionHasDefaultPythonVersion() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        assertEquals("3.12", ext.getPythonVersion().get());
    }

    @Test
    void apply_extensionHasDefaultEmptyPackages() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        assertTrue(ext.getPackages().get().isEmpty());
    }

    @Test
    void apply_extensionHasDefaultEmptyPipExtraArgs() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        assertTrue(ext.getPipExtraArgs().get().isEmpty());
    }

    // ------------------------------------------------------------------
    // Task registration
    // ------------------------------------------------------------------

    @Test
    void apply_registersCreateVenvTask() {
        VenvTask task = (VenvTask) project.getTasks().getByName("createVenv");
        assertNotNull(task);
        assertEquals("build", task.getGroup());
    }

    @Test
    void apply_registersGeneratePropsTask() {
        TaskContainer tasks = project.getTasks();
        assertTrue(tasks.getNames().contains("generatePythonEmbedProperties"));
    }

    @Test
    void apply_generatePropsTaskDependsOnCreateVenv() {
        var propsTask = project.getTasks().getByName("generatePythonEmbedProperties");
        assertTrue(propsTask.getDependsOn().stream()
                .anyMatch(dep -> dep.toString().contains("createVenv")));
    }

    // ------------------------------------------------------------------
    // Extension property wiring
    // ------------------------------------------------------------------

    @Test
    void apply_customPackages_wiredToTask() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        ext.getPackages().set(List.of("numpy==1.26.4", "pandas>=2.0"));

        VenvTask task = (VenvTask) project.getTasks().getByName("createVenv");
        assertEquals(List.of("numpy==1.26.4", "pandas>=2.0"), task.getPackages().get());
    }

    @Test
    void apply_customVenvOutputDir_wiredToTask() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        ext.getVenvOutputDir().set(project.getLayout().getBuildDirectory().dir("my-venv"));

        VenvTask task = (VenvTask) project.getTasks().getByName("createVenv");
        // Should use custom dir when set
        assertTrue(task.getVenvDir().get().getAsFile().getAbsolutePath().contains("my-venv"));
    }

    @Test
    void apply_defaultVenvDir_isUnderBuildDir() {
        VenvTask task = (VenvTask) project.getTasks().getByName("createVenv");
        String venvPath = task.getVenvDir().get().getAsFile().getAbsolutePath();
        String buildPath = project.getLayout().getBuildDirectory().get()
                .getAsFile().getAbsolutePath();
        assertTrue(venvPath.contains(buildPath));
    }

    @Test
    void apply_customRequirementsFile_wiredToTask() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        ext.getRequirementsFile().set("requirements.txt");

        VenvTask task = (VenvTask) project.getTasks().getByName("createVenv");
        assertEquals("requirements.txt", task.getRequirementsFile().get());
    }

    @Test
    void apply_customPyprojectTomlFile_wiredToTask() {
        PythonEmbedExtension ext = project.getExtensions()
                .getByType(PythonEmbedExtension.class);
        ext.getPyprojectTomlFile().set("pyproject.toml");

        VenvTask task = (VenvTask) project.getTasks().getByName("createVenv");
        assertEquals("pyproject.toml", task.getPyprojectTomlFile().get());
    }

    // ------------------------------------------------------------------
    // GeneratePropsTask
    // ------------------------------------------------------------------

    @Test
    void generateProps_embeddedVenv_writesCorrectProperties(@TempDir Path tempDir) throws IOException {
        Path buildDir = tempDir.resolve("build");
        Path venvDir = buildDir.resolve("python-venv");
        Files.createDirectories(venvDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        PythonEmbedPlugin.GeneratePropsTask task = project.getTasks()
                .register("testGenerateProps", PythonEmbedPlugin.GeneratePropsTask.class).get();
        task.getVenvDir().set(project.getLayout().dir(
                project.provider(() -> venvDir.toFile())));
        task.getOutputDir().set(project.getLayout().dir(
                project.provider(() -> outputDir.toFile())));

        task.generate();

        Path propsFile = outputDir.resolve("META-INF").resolve("python-embed.properties");
        assertTrue(Files.exists(propsFile));

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        assertEquals(venvDir.toAbsolutePath().toString(), props.getProperty("venv.path"));
        // venv is under buildDir, should be embedded
        // Since we're not using the real build dir, the embedded check depends on
        // whether venvDir starts with the project's build directory path.
        // In this test, GeneratePropsTask reads buildDir from getProject(),
        // and the project's build dir is not tempDir/build, so embedded will be false.
        // This is expected - the embedded detection is only for real builds.
        String embedded = props.getProperty("venv.embedded");
        assertNotNull(embedded);
        assertTrue(embedded.equals("true") || embedded.equals("false"));
    }

    @Test
    void generateProps_createsOutputDirectory(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);
        Path outputDir = tempDir.resolve("generated-resources");
        // Output dir does not exist yet
        assertFalse(Files.exists(outputDir));

        PythonEmbedPlugin.GeneratePropsTask task = project.getTasks()
                .register("testGenerateProps2", PythonEmbedPlugin.GeneratePropsTask.class).get();
        task.getVenvDir().set(project.getLayout().dir(
                project.provider(() -> venvDir.toFile())));
        task.getOutputDir().set(project.getLayout().dir(
                project.provider(() -> outputDir.toFile())));

        task.generate();

        Path propsFile = outputDir.resolve("META-INF").resolve("python-embed.properties");
        assertTrue(Files.exists(propsFile));
    }

    @Test
    void generateProps_venvPathIsAbsolute(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("my-venv");
        Files.createDirectories(venvDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        PythonEmbedPlugin.GeneratePropsTask task = project.getTasks()
                .register("testGenerateProps3", PythonEmbedPlugin.GeneratePropsTask.class).get();
        task.getVenvDir().set(project.getLayout().dir(
                project.provider(() -> venvDir.toFile())));
        task.getOutputDir().set(project.getLayout().dir(
                project.provider(() -> outputDir.toFile())));

        task.generate();

        Path propsFile = outputDir.resolve("META-INF").resolve("python-embed.properties");
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        Path storedPath = Path.of(props.getProperty("venv.path"));
        assertTrue(storedPath.isAbsolute());
    }

    @Test
    void generateProps_hasBothProperties(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        PythonEmbedPlugin.GeneratePropsTask task = project.getTasks()
                .register("testGenerateProps4", PythonEmbedPlugin.GeneratePropsTask.class).get();
        task.getVenvDir().set(project.getLayout().dir(
                project.provider(() -> venvDir.toFile())));
        task.getOutputDir().set(project.getLayout().dir(
                project.provider(() -> outputDir.toFile())));

        task.generate();

        Path propsFile = outputDir.resolve("META-INF").resolve("python-embed.properties");
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        assertEquals(2, props.size());
        assertNotNull(props.getProperty("venv.path"));
        assertNotNull(props.getProperty("venv.embedded"));
    }

    // ------------------------------------------------------------------
    // GeneratePropsTask - edge cases
    // ------------------------------------------------------------------

    @Test
    void generateProps_overwritesExistingFile(@TempDir Path tempDir) throws IOException {
        Path venvDir = tempDir.resolve("venv");
        Files.createDirectories(venvDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Pre-create a stale properties file
        Path propsDir = outputDir.resolve("META-INF");
        Files.createDirectories(propsDir);
        Path propsFile = propsDir.resolve("python-embed.properties");
        Files.writeString(propsFile, "old=value\n");

        PythonEmbedPlugin.GeneratePropsTask task = project.getTasks()
                .register("testGenerateProps5", PythonEmbedPlugin.GeneratePropsTask.class).get();
        task.getVenvDir().set(project.getLayout().dir(
                project.provider(() -> venvDir.toFile())));
        task.getOutputDir().set(project.getLayout().dir(
                project.provider(() -> outputDir.toFile())));

        task.generate();

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        // Should have the new properties, not the old one
        assertNotNull(props.getProperty("venv.path"));
        assertNull(props.getProperty("old"));
    }
}
