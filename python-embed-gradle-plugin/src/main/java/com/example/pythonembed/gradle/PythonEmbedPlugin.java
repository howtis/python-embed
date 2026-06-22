package com.example.pythonembed.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Gradle plugin that creates a Python environment and installs specified packages
 * at build time.
 *
 * <p>If system Python is available, a standard venv is used.
 * If not, a portable CPython is auto-downloaded from python-build-standalone.
 *
 * <p>The venv can be embedded in the JAR as a classpath resource (default)
 * or kept external and referenced via a generated properties file.
 *
 * <p>Usage:
 * <pre>{@code
 * plugins {
 *     id 'com.example.python-embed' version '1.0.0'
 * }
 * pythonEmbed {
 *     packages = ['numpy==1.26.4']
 *     venvOutputDir = layout.buildDirectory.dir('my-venv')
 * }
 * }</pre>
 */
public class PythonEmbedPlugin implements Plugin<Project> {

    private static final String EXTENSION_NAME = "pythonEmbed";
    private static final String TASK_NAME = "createVenv";
    private static final String PROPS_TASK_NAME = "generatePythonEmbedProperties";
    static final String PROPS_RESOURCE_PATH = "META-INF/python-embed.properties";
    static final String PROPS_KEY_VENV_PATH = "venv.path";
    static final String PROPS_KEY_VENV_EMBEDDED = "venv.embedded";

    @Override
    public void apply(Project project) {
        // Register extension
        PythonEmbedExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, PythonEmbedExtension.class);

        // Determine venv output directory
        DirectoryProperty venvOutputDir = extension.getVenvOutputDir();
        Directory defaultVenvDir = project.getLayout().getBuildDirectory().dir("python-venv").get();

        // Register the venv creation task
        TaskProvider<VenvTask> venvTask = project.getTasks().register(TASK_NAME, VenvTask.class, task -> {
            task.getVenvDir().set(venvOutputDir.isPresent()
                    ? venvOutputDir
                    : project.getLayout().getBuildDirectory().dir("python-venv"));
            task.getPackages().set(extension.getPackages());
            task.getRequirementsFile().set(extension.getRequirementsFile());
            task.getPyprojectTomlFile().set(extension.getPyprojectTomlFile());
            task.getPythonVersion().set(extension.getPythonVersion());
            task.getPipIndexUrl().set(extension.getPipIndexUrl());
            task.getPipExtraArgs().set(extension.getPipExtraArgs());
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.setDescription("Creates a Python venv and installs specified packages.");
        });

        // Generate python-embed.properties for runtime path discovery
        Directory generatedResourcesDir = project.getLayout().getBuildDirectory()
                .dir("generated-resources/python-embed").get();
        TaskProvider<?> propsTask = project.getTasks().register(PROPS_TASK_NAME, GeneratePropsTask.class, task -> {
            task.getVenvDir().set(venvOutputDir.isPresent()
                    ? venvOutputDir
                    : project.getLayout().getBuildDirectory().dir("python-venv"));
            task.getOutputDir().set(generatedResourcesDir);
            task.dependsOn(venvTask);
        });

        // Wire into Java plugin's resource processing if available
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions()
                    .getByType(SourceSetContainer.class);
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            // Add generated properties as a resource directory
            main.getResources().srcDir(generatedResourcesDir);

            // Add build/python-venv as a resource directory only if embedded
            // (i.e., venv is under the build directory)
            Directory actualVenvDir = venvOutputDir.isPresent()
                    ? venvOutputDir.get()
                    : defaultVenvDir;
            Path buildDirPath = project.getLayout().getBuildDirectory().get().getAsFile().toPath();
            Path venvPath = actualVenvDir.getAsFile().toPath().toAbsolutePath();
            Path buildPath = buildDirPath.toAbsolutePath();
            boolean embedded = venvPath.startsWith(buildPath);

            if (embedded) {
                main.getResources().srcDir(actualVenvDir);
            }

            // processResources depends on both createVenv and props generation
            project.getTasks().named(main.getProcessResourcesTaskName())
                    .configure(task -> {
                        task.dependsOn(venvTask);
                        task.dependsOn(propsTask);
                    });
        });
    }

    /**
     * Generates {@code META-INF/python-embed.properties} with the venv path
     * and embedded flag for runtime discovery.
     */
    public static abstract class GeneratePropsTask extends DefaultTask {

        /** The venv directory (input). */
        @org.gradle.api.tasks.InputDirectory
        public abstract DirectoryProperty getVenvDir();

        /** Output directory for the properties file (under resources). */
        @OutputDirectory
        public abstract DirectoryProperty getOutputDir();

        @TaskAction
        public void generate() {
            Path venvPath = getVenvDir().get().getAsFile().toPath().toAbsolutePath();
            Path buildDirPath = getProject().getLayout().getBuildDirectory().get()
                    .getAsFile().toPath().toAbsolutePath();
            boolean embedded = venvPath.startsWith(buildDirPath);

            Properties props = new Properties();
            props.setProperty(PROPS_KEY_VENV_PATH, venvPath.toString());
            props.setProperty(PROPS_KEY_VENV_EMBEDDED, String.valueOf(embedded));

            Path outputDir = getOutputDir().get().getAsFile().toPath();
            Path outputFile = outputDir.resolve(PROPS_RESOURCE_PATH);
            try {
                Files.createDirectories(outputFile.getParent());
                try (var writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                    props.store(writer, "PythonEmbed runtime configuration");
                }
                getLogger().lifecycle("Generated: {}", outputFile);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write " + PROPS_RESOURCE_PATH, e);
            }
        }
    }
}
